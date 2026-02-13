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
    : MUSIC_SYMBOL_LINE (SYMBOL_CHORD | SYMBOL_DECO | SYMBOL_DECO_PLUS | SYMBOL_SKIP | SYMBOL_BAR | SYMBOL_TEXT)*
    ;

music_line
    : (measure | field_in_body | lyrics_line | symbol_line)+ (NEWLINE | EOF)? # MusicLineContent
    | (EOL_MUSIC | NEWLINE)                                                 # MusicLineEmpty
    ;

measure
    : (variant | element)* barline    # MeasureWithBar
    | (variant | element)+            # MeasureNoBar
    ;

element
    : note_element           # Note
    | rest_element           # Rest
    | tuplet_element         # Tuplet
    | chord_alt              # Chord
    | annotation_alt         # Annotation
    | decoration_alt         # Decoration
    | inline_field_alt       # InlineField
    | stylesheet_directive_alt # Stylesheet
    | overlay_alt            # Overlay
    | grace_group_alt        # GraceGroup
    | slur_start_alt         # SlurStart
    | slur_end_alt           # SlurEnd
    | broken_rhythm_alt      # BrokenRhythm
    | spacer_alt             # Space
    | OCTAVE_UP              # Miscellaneous
    | OCTAVE_DOWN            # Miscellaneous
    | BRACKET_START          # Miscellaneous
    | BRACKET_END            # Miscellaneous
    | PLUS                   # Miscellaneous
    | MUSIC_TEXT             # Miscellaneous
    | MUSIC_BACKSLASH        # Miscellaneous
    | HYPHEN                 # Miscellaneous
    ;

chord_element
    : note_element      # ChordNote
    | rest_element      # ChordRest
    | decoration_alt    # ChordDecoration
    | annotation_alt    # ChordAnnotation
    | spacer_alt        # ChordSpace
    | OCTAVE_UP         # ChordMisc
    | OCTAVE_DOWN       # ChordMisc
    | MUSIC_TEXT        # ChordMisc
    | MUSIC_BACKSLASH   # ChordMisc
    ;

note_element
    : decoration_alt* accidental? note_pitch octave_modifier? note_length? tie?
    ;

rest_element
    : decoration_alt* REST note_length?
    ;

note_pitch
    : NOTE_PITCH
    ;

accidental
    : ACC_SHARP_HALF | ACC_SHARP | ACC_SHARP_QUART_3 | ACC_SHARP_DBL_HALF | ACC_SHARP_DBL | ACC_FLAT_HALF | ACC_FLAT | ACC_FLAT_QUART_3 | ACC_FLAT_DBL_HALF | ACC_FLAT_DBL | ACC_NATURAL
    ;

octave_modifier
    : (OCTAVE_UP | OCTAVE_DOWN)+
    ;

note_length
    : DIGIT+ (SLASH+ DIGIT*)?
    | SLASH+ DIGIT*
    ;

broken_rhythm_alt
    : BROKEN_RHYTHM_LEFT | BROKEN_RHYTHM_RIGHT
    ;

tie : HYPHEN ;

tuplet_element
    : TUPLET_START
    ;
    
chord_alt
    : decoration_alt* BRACKET_START chord_element* BRACKET_END note_length?
    ;

annotation_alt
    : CHORD_START CHORD_CONTENT? CHORD_END
    ;

decoration_alt
    : DECORATION_START (BANG_DECO_CONTENT | SPACE | BROKEN_RHYTHM_LEFT | BROKEN_RHYTHM_RIGHT)* DECORATION_END
    | DECORATION_START (BANG_DECO_CONTENT | SPACE | BROKEN_RHYTHM_LEFT | BROKEN_RHYTHM_RIGHT)* (NEWLINE | EOF)
    | PLUS_DECORATION (PLUS_DECO_CONTENT | SPACE)* PLUS_DECORATION_END
    | ROLL | UPBOW | DOWNBOW | PLUS | STACCATO | USER_DEF_SYMBOL
    ;
    
inline_field_alt
    : INLINE_FIELD_START INLINE_FIELD_CONTENT INLINE_FIELD_END
    ;
    
overlay_alt
    : OVERLAY
    ;
    
grace_group_alt
    : PREFIX_GRACE SLASH? (note_element | chord_alt | rest_element | tuplet_element | annotation_alt | decoration_alt | inline_field_alt | stylesheet_directive_alt | overlay_alt | slur_start_alt | slur_end_alt | broken_rhythm_alt | spacer_alt | OCTAVE_UP | OCTAVE_DOWN | BRACKET_START | BRACKET_END | PLUS | MUSIC_TEXT | MUSIC_BACKSLASH | HYPHEN)* SUFFIX_GRACE
    ;
    
slur_start_alt : SLUR_START ;
slur_end_alt : SLUR_END ;

barline
    : BAR_THIN_DOUBLE    # Bar
    | BAR_THIN_THICK     # Bar
    | BAR_THICK_THIN     # Bar
    | BAR_THICK_THICK    # Bar
    | BAR_REP_START      # Bar
    | BAR_REP_END        # Bar
    | BAR_REP_END_ALT    # Bar
    | BAR_REP_END_TUNE   # Bar
    | BAR_REP_DBL        # Bar
    | BAR_REP_DBL_ALT    # Bar
    | BAR_REP_DBL_TUNE   # Bar
    | BAR_SINGLE         # Bar
    | variant            # VariantBar
    ;

variant
    : (BRACKET_START | BAR_SINGLE) DIGIT+
    ;
    
tune_preamble
    : (field | STYLESHEET | text_block_default | HEADER_TEXT | NEWLINE | UNRECOGNIZED | FREE_TEXT | WS_DEFAULT | DEFAULT_COMMENT)*
    ;

tune_header
    : x_ref (field | STYLESHEET | text_block_header | HEADER_TEXT | NEWLINE | UNRECOGNIZED)* key_field
    ;

tune_body
    : (music_line | field_in_body | symbol_line | lyrics_line | text_block_music | NEWLINE | UNRECOGNIZED)*
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
    
stylesheet_directive_alt
    : STYLESHEET
    ;

spacer_alt
    : SPACE | BACKTICK | DOLLAR | COLON | SPACER | DIGIT | PLUS
    ;
