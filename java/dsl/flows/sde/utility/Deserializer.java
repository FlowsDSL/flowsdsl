// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.utility;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class Deserializer {
    public final FileChannel channel;
    public final ByteBuffer buffer;
    public final boolean tail;

    public Deserializer(String filename, boolean tail) throws Exception {
        channel = FileChannel.open(Path.of(filename));
        buffer = ByteBuffer.allocate(0x1000);
        this.tail = tail;
        buffer.limit(0);
        readMore(3);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        byte[] header = new byte[3];
        buffer.get(header, 0, 3);
        if (header[0] != 'I' || header[1] != 'S' || header[2] != 'F') {
            throw new Exception("Not an ISF file");
        }
    }

    private void readMore(int length) {
        while (buffer.remaining() < length) {
            buffer.compact();
            try {
                channel.read(buffer);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            buffer.flip();
            if (buffer.remaining() >= length) {
                return;
            }
            if (!tail) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    public String readString() {
        byte[] b = new byte[(int) readInteger()];
        readMore(b.length);
        buffer.get(b, 0, b.length);
        return new String(b, StandardCharsets.US_ASCII);
    }

    public long readInteger() {
        long result = 0;
        byte shift = 0;
        while (true) {
            readMore(1);
            byte b = buffer.get();
            result |= (b & 0x7fl) << shift;
            if (b >= 0) {
                if ((b & 0x40) != 0) {
                    result |= (-0x80l) << shift;
                }
                return result;
            }
            shift += 7;
        }
    }
}
