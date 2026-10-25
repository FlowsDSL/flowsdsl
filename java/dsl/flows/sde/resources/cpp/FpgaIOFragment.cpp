    int driverDeviceFileDescriptor = open(deviceName.c_str(), O_RDWR | O_SYNC | O_EXCL);
    if (driverDeviceFileDescriptor == -1) {
        throw FpgaException(std::string{"Could not open fpga device for writing: "} + strerror(errno));
    }
    if (flock(driverDeviceFileDescriptor, LOCK_EX | LOCK_NB) == -1) {
        throw FpgaException(std::string{"Could not lock fpga device: "} + strerror(errno));
    }
    struct {
        uint32_t command_queue_length;
        uint32_t from_card_queue_size;
        uint64_t from_card_queue_bus_address;
    } data{};
    readWithException(driverDeviceFileDescriptor, reinterpret_cast<uint8_t *>(&data), sizeof(data));
    volatile uint8_t *reportQueueAddress = static_cast<uint8_t *>(mmap(nullptr, data.from_card_queue_size,
                                                                       PROT_READ | PROT_WRITE, MAP_SHARED,
                                                                       driverDeviceFileDescriptor, 0));
    if (reportQueueAddress == MAP_FAILED) {
        throw FpgaException("Could not mmap fpga device for report queue");
    }
    volatile uint8_t *commandQueueAddress = static_cast<uint8_t *>(mmap(nullptr, data.command_queue_length,
                                                                        PROT_READ | PROT_WRITE, MAP_SHARED,
                                                                        driverDeviceFileDescriptor,
                                                                        data.from_card_queue_size));
    if (commandQueueAddress == MAP_FAILED) {
        throw FpgaException(std::string{"Could not mmap fpga device for command queue: "} + strerror(errno));
    }

    memset((void *) reportQueueAddress, 0xcc, data.from_card_queue_size);

    CommandSenderField.queueStart = (uint32_t*) commandQueueAddress;
    CommandSenderField.queueWriter = CommandSenderField.queueStart;
    CommandSenderField.queueLimit = CommandSenderField.queueStart + 0x200;
    CommandSenderField.nextWord = 0;
    CommandSenderField.bitPosition = 32;
    ReportReceiverField.queueStart = (uint32_t*) reportQueueAddress;
    ReportReceiverField.queueReader = ReportReceiverField.queueStart;
    ReportReceiverField.queueLimit = (uint32_t*) (reportQueueAddress + data.from_card_queue_size);
    ReportReceiverField.topBit = 0;
    ReportReceiverField.bitPosition = 0;
    ReportReceiverField.wordIndex = 0;

    CommandSenderField.queueStart[0x202] = 0xfffe0;
    CommandSenderField.queueStart[0x200] = data.from_card_queue_bus_address;
    CommandSenderField.queueStart[0x201] = data.from_card_queue_bus_address >> 32;

    auto timeout = std::chrono::high_resolution_clock::now() + std::chrono::milliseconds(1);
    while (true) {
        if (std::chrono::high_resolution_clock::now() >= timeout) {
            throw FpgaException("Did not get response from the card");
        }
        uint32_t wordA = ReportReceiverField.queueReader[0];
        uint32_t wordB = ReportReceiverField.queueReader[1];
        if (((wordA | wordB) >> 31) == 0) {
            if (wordA != InterfaceDigestA || wordB != InterfaceDigestB) {
                char received[17];
                snprintf(received, sizeof(received), "%08x%08x", wordA, wordB);
                char expected[17];
                snprintf(expected, sizeof(expected), "%08x%08x", InterfaceDigestA, InterfaceDigestB);
                throw FpgaException(std::string{"Interface digest "} + received + " does not match expected " + expected);
            }
            ReportReceiverField.queueReader += 2;
            ReportReceiverField.nextLimitUpdateByTime = timeout;
            ReportReceiverField.nextLimitUpdateByPointer = 0x10000;
            return;
        }
    }
}

void FpgaIO::CommandSenderType::write(uint32_t value, uint8_t width) {
    if (width != 32) {
        value &= (1u << width) - 1;
    }
    if (width > bitPosition) {
        width -= bitPosition;
        nextWord |= value >> width;
        flush();
    }
    bitPosition -= width;
    nextWord |= value << bitPosition;
}

void FpgaIO::CommandSenderType::pad(uint32_t width) {
    if (width > bitPosition) {
        width -= bitPosition;
        bitPosition = 0;
        flush();
        while (width > 32) {
            bitPosition = 0;
            width -= 32;
            flush();
        }
    }
    bitPosition -= width;
}

void FpgaIO::CommandSenderType::flush() {
    if (bitPosition != 32) {
        *queueWriter++ = nextWord;
        if (queueWriter == queueLimit) {
            queueWriter = queueStart;
        }
        nextWord = 0;
        bitPosition = 32;
    }
}

uint32_t FpgaIO::ReportReceiverType::read(uint8_t width) {
    uint32_t result{};
    while (width > bitPosition) {
        width -= bitPosition;
        result |= (*decoderBuffer & ((1u << bitPosition) - 1)) << width;
        decoderBuffer++;
        bitPosition = FROM_CORE_CHANNEL_WIDTH;
    }
    bitPosition -= width;
    return result | ((*decoderBuffer >> bitPosition) & ((1u << width) - 1));
}

void FpgaIO::ReportReceiverType::skip(size_t width) {
    if (width > bitPosition) {
        width -= bitPosition;
        decoderBuffer += (width / FROM_CORE_CHANNEL_WIDTH)+1;
        bitPosition = FROM_CORE_CHANNEL_WIDTH-(width % FROM_CORE_CHANNEL_WIDTH);
    } else {
        bitPosition -= width;
    }
}

uint32_t FpgaIO::GetReaderPointer() const {
    return ((uint8_t *) ReportReceiverField.queueReader - (uint8_t *) ReportReceiverField.queueStart) | (ReportReceiverField.topBit << 20);
}
