package dev.duetigh.arashirender.world;

/** One undoable/redoable block deletion: the packed cell key and the palette index it held. */
public record BlockEdit(long key, int paletteIndex) {
}
