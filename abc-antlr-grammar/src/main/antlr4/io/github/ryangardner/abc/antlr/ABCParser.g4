parser grammar ABCParser;

options { tokenVocab=ABCLexer; }

tunebook
    : tune_preamble tune* EOF
    ;

tune
    : tune_header tune_body EOL_MUSIC*
    ;
    
x_ref
    : X_REF_START (FIELD_CONTENT | FIELD_BACKSLASH)* (NEWLINE | EOF)
    ;
    
key_field
    : KEY_FIELD (FIELD_CONTENT | FIELD_BACKSLASH)* (NEWLINE | EOF)
    ;

field
    : FIELD_ID (FIELD_CONTENT | FIELD_BACKSLASH)* (NEWLINE | EOF)
    ;

field_in_body
    : (FIELD_ID | KEY_FIELD) (FIELD_CONTENT | FIELD_BACKSLASH)* (NEWLINE | EOL_MUSIC | EOF)
    | STYLESHEET (NEWLINE | EOL_MUSIC | EOF)?
    ;

symbol_line
    : MUSIC_SYMBOL_LINE (SYMBOL_CHORD | SYMBOL_DECO | SYMBOL_SKIP | SYMBOL_BAR)*
    ;

music_line
    : (measure | field_in_body | lyrics_line)+ (NEWLINE | EOF)?
    | (EOL_MUSIC | NEWLINE)
    ;

measure
    : (variant | element)* barline
    | (variant | element)+ 
    ;

element
    : note_element
    | rest_element
    | tuplet_element
    | chord
    | annotation
    | decoration
    | inline_field
    | stylesheet_directive
    | overlay
    | grace_group
    | slur_start
    | slur_end
    | broken_rhythm
    | space
    | OCTAVE_UP
    | OCTAVE_DOWN
    | DIGIT
    | BRACKET_START
    | BRACKET_END
    | PLUS
    | COLON
    | MUSIC_TEXT
    | MUSIC_BACKSLASH
    | SPACER
    | BACKTICK
    | DOLLAR
    | HYPHEN
    ;

chord_element
    : note_element
    | rest_element
    | decoration
    | annotation
    | space
    | OCTAVE_UP
    | OCTAVE_DOWN
    | DIGIT
    | MUSIC_TEXT
    | MUSIC_BACKSLASH
    | SPACER
    | BACKTICK
    | DOLLAR
    ;

note_element
    : decoration* accidental? note_pitch octave_modifier? note_length? tie?
    ;

rest_element
    : decoration* REST note_length?
    ;

note_pitch
    : NOTE_PITCH
    ;

accidental
    : ACC_SHARP_HALF | ACC_SHARP | ACC_SHARP_QUART_3 | ACC_SHARP_DBL_HALF | ACC_SHARP_DBL | ACC_FLAT_HALF | ACC_FLAT | ACC_FLAT_QUART_3 | ACC_FLAT_DBL_HALF | ACC_FLAT_DBL | ACC_NATURAL
    ;

octave_modifier
    : OCTAVE_UP+
    | OCTAVE_DOWN+
    ;

note_length
    : DIGIT+ (SLASH+ DIGIT*)?
    | SLASH+ DIGIT*
    ;

broken_rhythm
    : BROKEN_RHYTHM_LEFT | BROKEN_RHYTHM_RIGHT
    ;

tie : HYPHEN ;

tuplet_element
    : TUPLET_START
    ;
    
chord
    : decoration* BRACKET_START chord_element* BRACKET_END note_length?
    ;

annotation
    : CHORD_START CHORD_CONTENT? CHORD_END
    ;

decoration
    : DECORATION_START (BANG_DECO_CONTENT | SPACE)* DECORATION_END
    | DECORATION_START (BANG_DECO_CONTENT | SPACE)* (NEWLINE | EOF)
    | PLUS_DECORATION (PLUS_DECO_CONTENT | SPACE)* PLUS_DECORATION_END
    | ROLL | UPBOW | DOWNBOW | PLUS | STACCATO | USER_DEF_SYMBOL
    ;
    
inline_field
    : INLINE_FIELD_START INLINE_FIELD_CONTENT INLINE_FIELD_END
    ;
    
overlay
    : OVERLAY
    ;
    
grace_group
    : PREFIX_GRACE SLASH? (note_element | chord | rest_element | tuplet_element | annotation | decoration | inline_field | stylesheet_directive | overlay | slur_start | slur_end | broken_rhythm | space | OCTAVE_UP | OCTAVE_DOWN | DIGIT | BRACKET_START | BRACKET_END | PLUS | COLON | MUSIC_TEXT | MUSIC_BACKSLASH | SPACER | BACKTICK | DOLLAR | HYPHEN)* SUFFIX_GRACE
    ;
    
slur_start : SLUR_START ;
slur_end : SLUR_END ;

barline
    : BAR_THIN_DOUBLE | BAR_THIN_THICK | BAR_THICK_THIN | BAR_THICK_THICK | BAR_REP_START | BAR_REP_END | BAR_REP_END_ALT | BAR_REP_END_TUNE | BAR_REP_DBL | BAR_REP_DBL_ALT | BAR_REP_DBL_TUNE | BAR_SINGLE | variant
    ;

variant
    : BRACKET_START DIGIT+
    | BAR_SINGLE DIGIT+
    ;
    

tune_preamble
    : (field | STYLESHEET | text_block_default | HEADER_TEXT | NEWLINE)*
    ;

tune_header
    : x_ref (field | STYLESHEET | text_block_header | HEADER_TEXT | NEWLINE)* key_field
    ;

tune_body
    : (music_line | field_in_body | symbol_line | lyrics_line | text_block_music | NEWLINE)*
    ;

lyrics_line
    : LYRICS_FIELD (LYRIC_CONTENT | NEWLINE)
    ;

text_block_default
    : TEXT_BLOCK_START (TEXT_BLOCK_CONTENT | TEXT_BLOCK_PERCENT)* TEXT_BLOCK_END
    ;

text_block_header
    : HEADER_TEXT_BLOCK_START (TEXT_BLOCK_CONTENT | TEXT_BLOCK_PERCENT)* TEXT_BLOCK_END
    ;

text_block_music
    : MUSIC_TEXT_BLOCK_START (TEXT_BLOCK_CONTENT | TEXT_BLOCK_PERCENT)* TEXT_BLOCK_END
    ;
    
stylesheet_directive
    : STYLESHEET
    ;

space : SPACE ;