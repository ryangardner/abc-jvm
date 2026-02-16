lexer grammar ABCLexer;

@members {
    private boolean isDecoration() {
        for (int i = 1; ; i++) {
            int c = _input.LA(i);
            if (c == '!') return true;
            if (c == '\r' || c == '\n' || c == ' ' || c == '\t' || c == '|' || c == ':' || c == '[' || c == ']' || c == -1) return false;
        }
    }
}

tokens {
    STYLESHEET,
    NEWLINE,
    EOL_MUSIC,
    SPACE
}

// ============================================================================
// DEFAULT MODE: File Start / Between Tunes
// ============================================================================

// X: Reference number line.
// Transition: Default -> Header
X_REF_START : 'X:' -> pushMode(HEADER_MODE), pushMode(FIELD_VALUE_MODE);

// Comments/Stylesheets valid between tunes
TEXT_BLOCK_START : '%%begintext' ~[\r\n]* ([\r\n]+ | EOF) -> pushMode(TEXT_BLOCK_MODE) ;
DEFAULT_STYLESHEET_DIRECTIVE : '%%' ~[\r\n]* ([\r\n]+ | EOF) -> type(STYLESHEET) ;
DEFAULT_COMMENT : '%' ~[\r\n]* ;

// Skip whitespace between tunes
WS_DEFAULT : [ \t\r\n]+ ;

// Free text check - more permissive
FREE_TEXT : ~[X%]+ ;

// Catch-all for anything else in default mode to track unrecognized characters
UNRECOGNIZED : . ;

// ============================================================================
// HEADER MODE (Inside Tune Header)
// ============================================================================
mode HEADER_MODE;

    // Key Field ends the header
    // Key Field must start at the beginning of the line
    KEY_FIELD : { getCharPositionInLine() == 0 }? 'K:' -> pushMode(KEY_VALUE_MODE);
    
    // Lyrics Field transitions to LYRICS_MODE
    LYRICS_FIELD : 'w:' -> pushMode(LYRICS_MODE);

    // Standard Fields (Allow multi-char e.g. notC: Composer:)
    // Fields must start at the beginning of the line
    FIELD_ID : { getCharPositionInLine() == 0 }? [A-Za-z]+ ':' -> pushMode(FIELD_VALUE_MODE);

    // Stylesheets in header
    HEADER_TEXT_BLOCK_START : '%%begintext' ~[\r\n]* ([\r\n]+ | EOF) -> pushMode(TEXT_BLOCK_MODE) ;
    HEADER_STYLESHEET : '%%' ~[\r\n]* ([\r\n]+ | EOF) -> type(STYLESHEET);

    // Newlines are significant: they separate fields
    HEADER_NEWLINE : [\r\n]+ -> type(NEWLINE);

    // Skip indentation/spaces before fields
    HEADER_WS : [ \t]+ -> skip;

    // Comments allowed in header
    HEADER_COMMENT : '%' ~[\r\n]* -> skip ;

    // Capture everything else in header to avoid errors. Single char to avoid swallowing fields.
    HEADER_TEXT : . ;

// ============================================================================
// LYRICS MODE (Reading content of w:)
// ============================================================================
mode LYRICS_MODE;

    // Capture everything except newline as content
    LYRIC_CONTENT : ~[\r\n]+ ;
    
    // End of lyrics line
    LYRIC_EOL : [\r\n]+ -> type(NEWLINE), popMode;

// ============================================================================
// FIELD VALUE MODE (Reading content of T:, M:, etc)
// ============================================================================
mode FIELD_VALUE_MODE;

    // Consume content up to newline, but handle continuation
    FIELD_CONTENT : (~[\r\n\\])+ ;
    FIELD_CONTINUATION : '\\' [ \t]* [\r\n]+ -> skip ;
    FIELD_BACKSLASH : '\\' ; 
    
    // Newline ends the value and returns to HEADER_MODE
    FIELD_VALUE_END : [\r\n]+ -> type(NEWLINE), popMode;

// ============================================================================
// KEY VALUE MODE (Reading content of K:, transitions to Music)
// ============================================================================
mode KEY_VALUE_MODE;

    // Content: Emit FIELD_CONTENT token
    KEY_CONTENT : ~[\r\n]+ -> type(FIELD_CONTENT) ;
    
    // End of Key Line: Emit NEWLINE, transition to MUSIC
    // Pop KEY_VALUE_MODE, Pop HEADER_MODE, Push MUSIC_MODE
    KEY_END : [\r\n]+ -> type(NEWLINE), popMode, popMode, pushMode(MUSIC_MODE);

// ============================================================================
// MUSIC MODE
// ============================================================================
mode MUSIC_MODE;

    // Transition back to DEFAULT if we see X: at the start of a line
    // (We use a predicate to check column position)
    X_REF_RESTART : { getCharPositionInLine() == 0 }? 'X:' -> type(X_REF_START), popMode, pushMode(HEADER_MODE), pushMode(FIELD_VALUE_MODE);

    // Fields inside Music (e.g. V:1, K:D, M:4/4 changed mid-tune)
    // Only at start of line
    MUSIC_KEY : { getCharPositionInLine() == 0 }? 'K:' -> type(KEY_FIELD), pushMode(KEY_VALUE_MODE);
    MUSIC_SYMBOL_LINE : { getCharPositionInLine() == 0 }? 's:' -> pushMode(SYMBOL_LINE_MODE);
    MUSIC_FIELD : { getCharPositionInLine() == 0 }? [A-Za-z] ':' -> type(FIELD_ID), pushMode(FIELD_VALUE_MODE);
    
    // Directives and Comments in Music
    MUSIC_TEXT_BLOCK_START : '%%begintext' ~[\r\n]* ([\r\n]+ | EOF) -> pushMode(TEXT_BLOCK_MODE) ;
    MUSIC_STYLESHEET_DIRECTIVE : '%%' ~[\r\n]* -> type(STYLESHEET) ;
    MUSIC_COMMENT : '%' ~[\r\n]* -> skip ;
    
    // Inline Fields
    INLINE_FIELD_START : '[' [A-Za-z] ':' -> pushMode(INLINE_FIELD_MODE) ;
    
    CHORD_START : '"' -> pushMode(CHORD_MODE);
    DECORATION_START : '!' { isDecoration() }? -> pushMode(BANG_DECO_MODE) ;
    BANG : '!' ;
    PLUS_DECORATION  : '+' -> pushMode(PLUS_DECO_MODE);

    BRACKET_START : '[' ;
    BRACKET_END   : ']' ;

    // ... Musical Tokens ...
    
    BAR_REP_END_TUNE : ':|]' ;
    BAR_REP_END_ALT  : ':]' ;
    BAR_REP_END      : ':|' ;
    BAR_REP_DBL_ALT  : ':|:' ;
    BAR_REP_DBL_TUNE : '::|]' ;
    BAR_REP_DBL      : '::' ;
    BAR_THICK_THICK  : '[|]' ;
    BAR_THIN_DOUBLE : '||' ;
    BAR_THIN_THICK  : '|]' ;
    BAR_THICK_THIN  : '[|' ;
    BAR_REP_START   : '|:' ;
    BAR_SINGLE      : '|' ;

    TUPLET_START : '(' [0-9]+ (':' [0-9]* (':' [0-9]*)?)? ;
    SLUR_START   : '(' ;
    SLUR_END     : ')' ;
    
    // Note/Rest followed by length
    NOTE_PITCH : [A-Ga-g] ;
    REST : [zZxX] ;
    SPACER : 'y' ;
    
    // 4.14 Decorations / 4.16 Redefinable Symbols
    ROLL : '~' ;
    UPBOW : 'u' ;
    DOWNBOW : 'v' ;
    USER_DEF_SYMBOL : [H-Wh-w] ;
    
    ACC_SHARP_DBL_HALF : '^^/' ;
    ACC_SHARP_DBL      : '^^' ;
    ACC_SHARP_QUART_3  : '^3/2' ;
    ACC_SHARP_HALF     : '^/' ;
    ACC_SHARP          : '^' ;
    ACC_FLAT_DBL_HALF  : '__/' ;
    ACC_FLAT_DBL       : '__' ;
    ACC_FLAT_QUART_3   : '_3/2' ;
    ACC_FLAT_HALF      : '_/' ;
    ACC_FLAT           : '_' ;
    ACC_NATURAL        : '=' ;
    
    STACCATO : '.' ;
    BACKTICK : '`' ;
    DOLLAR   : '$' ;
    PLUS     : '+' ;
    COLON    : ':' ;

    OCTAVE_UP   : '\'' ;
    OCTAVE_DOWN : ',' ;

    DIGIT : [0-9] ;
    SLASH : '/' ;
    BROKEN_RHYTHM_LEFT : '<'+ ;
    BROKEN_RHYTHM_RIGHT : '>'+ ;
    
    PREFIX_GRACE : '{' ;
    SUFFIX_GRACE : '}' ;
    
    HYPHEN : '-' ;
    OVERLAY : '&' ;
    
    WS_MUSIC : [ \t]+ -> type(SPACE) ;
    LINE_CONTINUATION : '\\' [ \t]* [\r\n]+ -> skip ;
    MUSIC_BACKSLASH : '\\' ;
    EOL_MUSIC : [\r\n]+ -> type(NEWLINE);
    
    // Catch-all
    MUSIC_TEXT : . ;

// ============================================================================
// HELPER MODES
// ============================================================================
mode INLINE_FIELD_MODE;
    INLINE_FIELD_END : ']' -> popMode ;
    INLINE_FIELD_CONTENT : ~']'+ ;

mode CHORD_MODE;
    CHORD_END : '"' -> popMode ;
    CHORD_CONTENT : ~'"'+ ;

mode BANG_DECO_MODE;
    DECORATION_END : '!' -> popMode ;
    BANG_DECO_CONTENT : ~[\r\n!]+ ;
    BANG_DECO_NEWLINE : [\r\n] -> type(NEWLINE), popMode ;

mode PLUS_DECO_MODE;
    PLUS_DECORATION_END : '+' -> popMode ;
    PLUS_DECO_CONTENT : (~[\r\n+])+ ;
    PLUS_DECO_NEWLINE : [\r\n] -> type(NEWLINE), popMode ;

// ============================================================================
// SYMBOL LINE MODE (Reading content of s:)
// ============================================================================
mode SYMBOL_LINE_MODE;
    
    // Symbol Tokens
    SYMBOL_CHORD : '"' ~'"'* '"' ;
    SYMBOL_DECO : '!' ~'!'* '!' ;
    SYMBOL_DECO_PLUS : '+' ~'+'* '+' ;
    SYMBOL_SKIP : '*' ;
    
    // Separators
    SYMBOL_BAR : '|' ;
    
    // Whitespace
    SYMBOL_WS : [ \t]+ -> skip ;
    
    // End of line
    SYMBOL_EOL : [\r\n]+ -> type(NEWLINE), popMode ;
    
    // Catch deviations (or should we allow generic text?)
    // Spec says "symbol line contains only !...! ... "..." ... *
    // Let's allow generic text if it doesn't match above.
    SYMBOL_TEXT : ~[\r\n \t"|!+*]+ ;
    UNRECOGNIZED_SYMBOL : . ;

// ============================================================================
// TEXT BLOCK MODE (%%begintext ... %%endtext)
// ============================================================================
mode TEXT_BLOCK_MODE;

    TEXT_BLOCK_END : '%%endtext' -> popMode ;
    
    // Capture content until endtext. Non-greedy.
    // Note: We need to handle newlines within content.
    // Ideally we want to capture everything as one token or line by line.
    // Let's capture generic text.
    TEXT_BLOCK_CONTENT : ~'%' + ; 
    // If we hit %, check if it is part of %%endtext.
    // If not, consume %.
    TEXT_BLOCK_PERCENT : '%' ;
