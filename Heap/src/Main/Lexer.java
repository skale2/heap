package Main;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;

import Helpers.*;


public class Lexer {
    public Lexer(BufferedReader text) {
        _text = text;
        _peekChars = new ArrayDeque();
        _EOF = false;
        advance();
    }

    /** Whether there are no more tokens in the file */
    public boolean isEmpty() { return _EOF; }

    /** Advances pointer in the filestream by one step
     * */
    private char advance() {
        try {
            if (!_peekChars.isEmpty())
                _current = (char) _peekChars.poll();
            else
                _current = (char) _text.read();
            if (_current == '\uFFFF')
                _EOF = true;
            return _current;
        } catch (IOException io) {
            _EOF = true;
        } return EMPTY;
    }

    /** Advances pointer in the filestream by NUM steps
     * */
    private char advance(int num) {
        try {
            while(num > 0) {
                if (!_peekChars.isEmpty())
                    _current = (char) _peekChars.poll();
                else
                    _current = (char) _text.read();
                num--;
            }
            return _current;
        } catch (IOException io) {
            _EOF = true;
        } return EMPTY;
    }

    /** Allows for peeking ahead one char in the filestram
     * */
    private char peek() {
        try {
            if (!_peekChars.isEmpty()) {
                return (char) _peekChars.peek();
            }
            char ch = (char) _text.read();
            _peekChars.offer(ch);
            return ch;
        } catch (IOException io) {
            return EMPTY;
        }
    }

    /** Allows for peeking ahead NUM chars in the filestream
     * */
    private char peek(int num) {
        try {
            char ch = EMPTY;
            while(num > 0) {
                if (!_peekChars.isEmpty())
                    ch = (char) _peekChars.peek();
                else {
                    ch = (char) _text.read();
                    _peekChars.offer(ch);
                }
                num--;
            }
            return ch;
        } catch (IOException io) {
            return EMPTY;
        }
    }

    private Token getString(char type) {
        StringBuilder string = new StringBuilder();
        advance();
        while (_current != type) {
            string.append(_current);
            advance();
        }
        advance();
        return new Token(string.toString(), Token.TokenType.STR_VAL);
    }

    private Token getNumber() {
        StringBuilder number = new StringBuilder();
        if (_current == '-') {
            number.append('-');
            advance();

        }
        Token.TokenType type = Token.TokenType.INT_VAL;
        while (Character.isDigit(_current) || _current == '.') {
            if (peek() == '.') {
                type = Token.TokenType.REAL_VAL;
            }
            number.append(_current);
            advance();
        }
        return new Token(number.toString(), type);
    }

    private Token getIdentifier() {
        StringBuilder id = new StringBuilder();
        TokenTypeTrie.TrieNode node = Token.TokenType.reserved.root();

        /* Keep advancing through the text, adding each character to the
        * final string, and checking to see if the string is still in the
        * reserved words trie. If it isn't, only add to the final string.
        */
        while(Character.isAlphabetic(_current) ||
                Character.isDigit(_current) ||
                _current == '_') {
            if (node != null) {
                node = node.getChild(_current);
            }
            id.append(_current);
            advance();
        }

        /* If the string of characters is still in the reserved words trie
        * and the final character has a token associated with it, return
        * that token
        * */
        if (node != null && node.tokenType() != null) {
            return new Token(node.tokenType());
        }
        /* Else, return the string as a variable token */
        return new Token(id.toString(), Token.TokenType.VAR);
    }

    private void skipWhitespace() {
        while (_current == EMPTY) { advance(); }
    }

    private void skipComment(char type) {
        if (type == '*') {
            while (advance() != type && peek() != '/') {}
        } else if (type == '/') {
            while (advance() != '\n') {}
        }
    }


    public Token next() {
        if (_current == ' ') {
            skipWhitespace();
            return next();
        } else if (_current == '"' || _current == '\'') {
            return getString(_current);
        } else if (Character.isDigit(_current)) {
            return getNumber();
        } else if (Character.isLetter(_current) || _current == '_') {
            return getIdentifier();
        } else if (_current == '-' && peek() == '[') {
            advance(2);
            return new Token(Token.TokenType.L_ARR_OPEN);
        } else if (_current == '{') {
            if (peek() == '>') {
                if (peek(2) == '<' && peek(3) == '}'){
                    advance(4);
                    return new Token(Token.TokenType.SET_TYPE);
                }
                advance(2);
                return new Token(Token.TokenType.SET_OPEN);
            } else if (peek() == '}') {
                advance(2);
                return new Token(Token.TokenType.MAP_TYPE);
            }
            advance();
            return new Token(Token.TokenType.SCOPE_OPEN);
        } else if (_current == '/') {
            if (peek() == '*') {
                skipComment('*');
                return next();
            } else if (peek() == '/') {
                skipComment('/');
                return next();
            } else if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.DIVIDE_EQ);
            }
            advance();
            return new Token(Token.TokenType.DIVIDE);
        } else if (_current == '=') {
            if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.EQUAL);
            } else if (peek() == '>') {
                advance(2);
                return new Token(Token.TokenType.DIRECT);
            }
            advance();
            return new Token(Token.TokenType.ASSIGN);
        } else if (_current == ':') {
            if (peek() == '=') {
                if (peek(2) == '=') {
                    advance(3);
                    return new Token(Token.TokenType.CAST_EQUAL);
                }
                advance(2);
                return new Token(Token.TokenType.CAST_ASSIGN);
            } else if (peek() == '!' && peek(2) == '=') {
                advance(3);
                return new Token(Token.TokenType.CAST_NOT_EQUAL);
            }
            advance();
            return new Token(Token.TokenType.COLON);
        } else if (_current == '&') {
            if (peek() == '&') {
                if (peek() == '=') {
                    advance(3);
                    return new Token(Token.TokenType.L_AND_EQ);
                }
                advance(2);
                return new Token(Token.TokenType.L_AND);
            } else if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.B_AND_EQ);
            }
            advance();
            return new Token(Token.TokenType.B_AND);
        } else if (_current == '|') {
            if (peek() == '|') {
                if (peek() == '=') {
                    advance(3);
                    return new Token(Token.TokenType.L_OR_EQ);
                }
                advance(2);
                return new Token(Token.TokenType.L_OR);
            } else if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.B_OR_EQ);
            } else if (peek() == '>') {
                advance(2);
                return new Token(Token.TokenType.PIPELINE);
            }
            advance();
            return new Token(Token.TokenType.B_OR);
        } else if (_current == '^') {
            if (peek() == '^') {
                if (peek() == '=') {
                    advance(3);
                    return new Token(Token.TokenType.L_XOR_EQ);
                }
                advance(2);
                return new Token(Token.TokenType.L_XOR);
            } else if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.B_XOR_EQ);
            }
            advance();
            return new Token(Token.TokenType.B_XOR);
        } else if (_current == '!') {
            if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.NOT_EQUAL);
            }
            advance();
            return new Token(Token.TokenType.L_NOT);
        } else if (_current == '<') {
            if (peek() == '<') {
                advance(2);
                return new Token(Token.TokenType.SHIFT_LEFT);
            } else if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.LESS_THAN_EQ);
            } else if (peek() == '}') {
                advance(2);
                return new Token(Token.TokenType.SET_CLOSE);
            } else if (peek() == '-') {
                if (peek(2) == '*') {
                    advance(3);
                    return new Token(Token.TokenType.DIR_CLOSE);
                } else if (peek() == '>') {
                    advance(3);
                    return new Token(Token.TokenType.DIR_2_EDGE);
                }
            }
            advance();
            return new Token(Token.TokenType.LESS_THAN);
        } else if (_current == '*') {
            if (peek() == '*') {
                advance(2);
                return new Token(Token.TokenType.EXP);
            } else if (peek() == '-') {
                if (peek(2) == '>') {
                    if (peek(3) == '*') {
                        advance(4);
                        return new Token(Token.TokenType.DIR_TYPE);
                    }
                    advance(3);
                    return new Token(Token.TokenType.DIR_OPEN);
                } else if (peek(2) == '*') {
                    advance(3);
                    return new Token(Token.TokenType.UNDIR_TYPE);
                }
                advance(2);
                return new Token(Token.TokenType.UNDIR_OPEN);
            } else if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.MULTIPLY_EQ);
            }
            advance();
            return new Token(Token.TokenType.MULTIPLY);
        } else if (_current == '-') {
            if (peek() == '*') {
                advance(2);
                return new Token(Token.TokenType.UNDIR_CLOSE);
            } else if (peek() == '-') {
                advance(2);
                return new Token(Token.TokenType.DECREMENT);
            } else if (peek() == '[') {
                advance(2);
                return new Token(Token.TokenType.L_ARR_OPEN);
            } else if (peek() == '>') {
                advance(2);
                return new Token(Token.TokenType.DIR_EDGE);
            } else if (peek() == '/') {
                if (peek() == '=') {
                    advance(3);
                    return new Token(Token.TokenType.FLOOR_EQ);
                }
                advance(2);
                return new Token(Token.TokenType.FLOOR);
            } else if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.SUBTRACT_EQ);
            } else if (Character.isDigit(peek())) {
                return getNumber();
            }
            advance();
            return new Token(Token.TokenType.SUBTRACT);
        } else if (_current == '[') {
            if (peek() == ']') {
                advance(2);
                return new Token(Token.TokenType.ARR_TYPE);
            }
            advance();
            return new Token(Token.TokenType.ARR_OPEN);
        } else if (_current == '+') {
            if (peek() == '+') {
                advance(2);
                return new Token(Token.TokenType.INCREMENT);
            } else if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.ADD_EQ);
            }
            advance();
            return new Token(Token.TokenType.ADD);
        } else if (_current == '>') {
            if (peek() == '>') {
                advance(2);
                 return new Token(Token.TokenType.SHIFT_RIGHT);
            } else if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.GREATER_THAN_EQ);
            }
            advance();
            return new Token(Token.TokenType.GREATER_THAN);
        } else if (_current == '%') {
            if (peek() == '=') {
                advance(2);
                return new Token(Token.TokenType.MOD_EQ);
            }
            advance();
            return new Token(Token.TokenType.MOD);
        } else if (_current == '.') {
            if (Character.isDigit(peek())) {
                return getNumber();
            }
            advance();
            return new Token(Token.TokenType.PERIOD);
        } else if (_current == '?') {
            if (peek() == '?') {
                advance(2);
                return new Token(Token.TokenType.NULL_COALESCE);
            } else if (peek() == '.') {
                advance(2);
                return new Token(Token.TokenType.OPT_CHAIN);
            }
            advance();
            return new Token(Token.TokenType.TERNARY);
        }


        switch(_current) {
            case ',': advance(); return new Token(Token.TokenType.COMMA);
            case ';': advance(); return new Token(Token.TokenType.EOL);
            case '`': advance(); return new Token(Token.TokenType.ROUND);
            case ']': advance(); return new Token(Token.TokenType.ARR_CLOSE);
            case '}': advance(); return new Token(Token.TokenType.SCOPE_CLOSE);
            case '(': advance(); return new Token(Token.TokenType.PAR_OPEN);
            case ')': advance(); return new Token(Token.TokenType.PAR_CLOSE);
            case '~': advance(); return new Token(Token.TokenType.B_NOT);
            case '@': advance(); return new Token(Token.TokenType.ANNOTATION);
            case '#': advance(); return new Token(Token.TokenType.TOTAL_REF);
            case '&': advance(); return new Token(Token.TokenType.DEREF);
            default:
                System.out.println("Error: Syntax");
                return null;
        }
    }

    private static char EMPTY = ' ';
    private boolean _EOF;
    private char _current;
    private ArrayDeque _peekChars;
    private BufferedReader _text;
}
