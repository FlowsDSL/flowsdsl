// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.utility;

public class LineSplitter {
    public static String[] splitLines(String text) {
        int length = text.length(), count = 1;
        for (int index = 0; index < length; index++) {
            if (text.charAt(index) == '\n') {
                count++;
            }
        }
        String[] result = new String[count];
        count = 0;
        int start = 0;
        for (int index = 0; index < length; index++) {
            if (text.charAt(index) == '\n') {
                result[count++] = text.substring(start, index);
                start = index + 1;
            }
        }
        result[count] = text.substring(start);
        return result;
    }
}
