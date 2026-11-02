// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.build;

import dsl.flows.sde.parser.SourceLocation;

class VivadoOutputParser {
    final String input;
    int position;

    VivadoOutputParser(String input) {
        this.input = input;
    }

    Integer parseInt() {
        int result = 0;
        boolean anyDigits = false;
        while (position < input.length()) {
            char ch = input.charAt(position);
            int digit = Character.getNumericValue(ch);
            if (digit == -1) {
                break;
            }
            anyDigits = true;
            result = result * 10 + digit;
            position++;
        }
        return anyDigits ? result : null;
    }

    String parseWord() {
        int start = position;
        while (position < input.length()) {
            char ch = input.charAt(position);
            if (ch != '_' && !Character.isLetter(input.charAt(position))) {
                break;
            }
            position++;
        }
        return position != start ? input.substring(start, position) : null;
    }

    boolean expect(char ch) {
        if (position < input.length() && ch == input.charAt(position)) {
            position++;
            return true;
        }
        return false;
    }

    BuildResultParser.ParseRegisterResult parseRegister() {
        if (!"CoreInstance".equals(parseWord())) {
            return null;
        }
        if (!expect('/')) {
            return null;
        }
        String instanceName = parseWord();
        if (!expect('/')) {
            return null;
        }
        String word = parseWord();
        if (!"Current_reg".equals(word) && !"Current".equals(word)) {
            return null;
        }
        if (!expect('[')) {
            return null;
        }
        String variableName = parseWord();
        Integer lineNumber = parseInt();
        SourceLocation location;
        if (lineNumber != null) {
            if (!expect('_')) {
                return null;
            }
            Integer positionNumber = parseInt();
            if (positionNumber == null) {
                return null;
            }
            location = new SourceLocation(lineNumber - 1, positionNumber - 1);
        } else {
            location = null;
        }
        if (!expect(']')) {
            return null;
        }
        return new BuildResultParser.ParseRegisterResult(instanceName, variableName, location);
    }
}
