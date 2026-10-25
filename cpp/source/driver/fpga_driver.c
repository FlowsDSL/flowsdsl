// SPDX-License-Identifier: GPL-2.0-only OR MIT
// Copyright 2026 Flows author <flows.dsl@proton.me>

#include <linux/mm.h>
#include <linux/pci.h>
#include <linux/init.h>
#include <linux/cdev.h>
#include <linux/module.h>
#include <linux/dma-mapping.h>
#define DMA_SIZE_SHIFT 20
#define EXPECTED_BAR1_SIZE 0x1000

static resource_size_t command_queue_start = 0;
static struct class *device_class = NULL;
static dev_t device_number = 0;
static struct cdev device = {};
static struct device *device_variable = NULL;
static unsigned char *virtual_address = NULL;
static dma_addr_t bus_address = 0;
static struct pci_dev *device_variable_pci = NULL;
static int devices_found = 0;

static const struct pci_device_id pci_ids[] = { { PCI_DEVICE(0x10ee, 0x903f) }, {} };
MODULE_DEVICE_TABLE(pci, pci_ids);

static pci_ers_result_t error_detected(struct pci_dev *dev, pci_channel_state_t state) {
    pr_err("error_detected\n");
    return PCI_ERS_RESULT_NEED_RESET;
}

static pci_ers_result_t slot_reset(struct pci_dev *dev) {
    pr_err("slot_reset\n");
    return PCI_ERS_RESULT_DISCONNECT;
}

static void resume(struct pci_dev *dev) {
    pr_err("resume\n");
}

static void reset_prepare(struct pci_dev *dev) {
    pr_err("reset_prepare\n");
}

static void reset_done(struct pci_dev *dev) {
    pr_err("reset_done\n");
}

static int probe(struct pci_dev *pdev, const struct pci_device_id *id) {
    if (devices_found == 1) {
        pr_err("multiple devices found: %s\n", dev_name(&pdev->dev));
        return -EBUSY;
    }
    pr_info("probing device %s version 1017\n", dev_name(&pdev->dev));
    int result = pci_enable_device(pdev);
    if (result != 0) {
        pr_err("failed to enable device: %d\n", result);
        return result;
    }
    if (pci_resource_len(pdev, 1) != EXPECTED_BAR1_SIZE) {
        pr_err("BAR1 size is not the same size as expected (%u)\n", EXPECTED_BAR1_SIZE);
        result = -EINVAL;
        goto disable_device;
    }
    result = pci_request_regions(pdev, "fpga_driver");
    if (result != 0) {
        pr_err("failed to request regions: %d\n", result);
        goto disable_device;
    }
    pci_set_master(pdev);
    result = dma_set_mask_and_coherent(&pdev->dev, DMA_BIT_MASK(64));
    if (result != 0) {
        pr_err("failed to set DMA bitmask\n");
        goto release_regions;
    }
    virtual_address = dma_alloc_coherent(&pdev->dev, 1 << DMA_SIZE_SHIFT, &bus_address, GFP_KERNEL);
    if (virtual_address == NULL) {
        pr_err("failed to allocate dma address\n");
        result = -ENOMEM;
        goto release_regions;
    }
    if ((bus_address & ((1 << DMA_SIZE_SHIFT) - 1)) != 0) {
        pr_err("bus_address is not aligned to DMA buffer size\n");
        result = -EINVAL;
        goto free_dma;
    }
    command_queue_start = pci_resource_start(pdev, 1);
    device_variable_pci = pdev;
    devices_found++;
    return 0;

free_dma:
    dma_free_coherent(&pdev->dev, 1 << DMA_SIZE_SHIFT, virtual_address, bus_address);
    virtual_address = NULL;
    bus_address = 0;
release_regions:
    pci_clear_master(pdev);
    pci_release_regions(pdev);
disable_device:
    pci_disable_device(pdev);
    return result;
}

static void remove(struct pci_dev *pdev) {
    if (pdev != device_variable_pci) {
        return;
    }
    dma_free_coherent(&pdev->dev, 1 << DMA_SIZE_SHIFT, virtual_address, bus_address);
    virtual_address = NULL;
    bus_address = 0;
    command_queue_start = 0;
    pci_clear_master(pdev);
    pci_release_regions(pdev);
    pci_disable_device(pdev);
    device_variable_pci = NULL;
    devices_found--;
}

static const struct pci_error_handlers err_handlers = {
    .error_detected = error_detected, .slot_reset = slot_reset, .resume = resume, .reset_prepare = reset_prepare, .reset_done = reset_done };

static struct pci_driver pci_driver_variable = {
    .name = "fpga_dma_driver", .id_table = pci_ids, .probe = probe, .remove = remove, .err_handler = &err_handlers };

static int device_open_or_close(struct inode *inode, struct file *file) {
    return 0;
}

struct device_info {
    u32 command_queue_length;
    u32 from_card_queue_size;
    u64 from_card_queue_bus_address;
};

static ssize_t device_read(struct file *file, char __user *buffer, size_t size, loff_t *offset) {
    struct device_info data;
    if (*offset != 0 || size != sizeof(data)) {
        return -EINVAL;
    }
    if (device_variable_pci == NULL) {
        return -ENODEV;
    }
    data.command_queue_length = EXPECTED_BAR1_SIZE;
    data.from_card_queue_size = 1 << DMA_SIZE_SHIFT;
    data.from_card_queue_bus_address = bus_address;
    if (copy_to_user(buffer, &data, size) != 0) {
        return -EFAULT;
    }
    *offset += size;
    return size;
}

static int device_mmap(struct file *file, struct vm_area_struct *vma) {
    int remap_result;
    if (device_variable_pci == NULL) {
        return -ENODEV;
    }
    if (vma->vm_pgoff == 0 && vma->vm_end - vma->vm_start == (1 << DMA_SIZE_SHIFT)) {
        remap_result = dma_mmap_coherent(&device_variable_pci->dev, vma, virtual_address, bus_address, 1 << DMA_SIZE_SHIFT);
    } else if (vma->vm_pgoff == ((1 << DMA_SIZE_SHIFT) >> PAGE_SHIFT) && vma->vm_end - vma->vm_start == EXPECTED_BAR1_SIZE) {
        vma->vm_page_prot = pgprot_noncached(vma->vm_page_prot);
        remap_result = io_remap_pfn_range(vma, vma->vm_start, command_queue_start >> PAGE_SHIFT, EXPECTED_BAR1_SIZE, vma->vm_page_prot);
    } else {
        return -EINVAL;
    }
    if (remap_result < 0) {
        pr_err("mmap failed with result %d\n", remap_result);
        return -EIO;
    }
    pr_info("mmap succeeded\n");
    return 0;
}

static const struct file_operations fops = {
    .owner = THIS_MODULE, .open = device_open_or_close, .release = device_open_or_close, .read = device_read, .mmap = device_mmap };

static char *my_devnode(const struct device *dev, umode_t *mode) {
    if (mode) {
        *mode = 0666;
    }
    return NULL;
}

static int create_device(void) {
    int result;
    device_class = class_create("fpga_dma_class");
    if (IS_ERR(device_class)) {
        result = PTR_ERR(device_class);
        pr_err("class_create failed: %d\n", result);
        device_class = NULL;
        return result;
    }
    device_class->devnode = my_devnode;
    result = alloc_chrdev_region(&device_number, 0, 1, "fpga_dma_region");
    if (result != 0) {
        pr_err("alloc_chrdev_region failed: %d\n", result);
        goto destroy_class;
    }
    cdev_init(&device, &fops);
    result = cdev_add(&device, device_number, 1);
    if (result != 0) {
        pr_err("cdev_add failed: %d\n", result);
        goto unregister_region;
    }
    device_variable = device_create(device_class, NULL, device_number, NULL, "fpga_dma_device");
    if (IS_ERR(device_variable)) {
        result = PTR_ERR(device_variable);
        pr_err("device_create failed: %d\n", result);
        device_variable = NULL;
        goto delete_cdev;
    }
    return 0;

delete_cdev:
    cdev_del(&device);
unregister_region:
    unregister_chrdev_region(device_number, 1);
destroy_class:
    class_destroy(device_class);
    device_class = NULL;
    return result;
}

static void destroy_device(void) {
    device_destroy(device_class, device_number);
    cdev_del(&device);
    unregister_chrdev_region(device_number, 1);
    class_destroy(device_class);
}

static int __init mod_init(void) {
    int result;
    pr_info("registering pci driver\n");
    result = pci_register_driver(&pci_driver_variable);
    if (result != 0) {
        pr_err("pci_register_driver failed with result %d\n", result);
        return result;
    }
    if (devices_found == 0) {
        pr_err("no devices found\n");
        pci_unregister_driver(&pci_driver_variable);
        return -ENODEV;
    }
    pr_info("creating device\n");
    result = create_device();
    if (result != 0) {
        pci_unregister_driver(&pci_driver_variable);
        return result;
    }
    return 0;
}

static void __exit mod_exit(void) {
    destroy_device();
    pr_info("unregistering fpga_dma_device driver\n");
    pci_unregister_driver(&pci_driver_variable);
}

module_init(mod_init);
module_exit(mod_exit);

MODULE_LICENSE("Dual MIT/GPL");
