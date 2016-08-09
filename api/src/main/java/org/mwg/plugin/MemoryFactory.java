package org.mwg.plugin;

import org.mwg.Graph;
import org.mwg.chunk.ChunkSpace;
import org.mwg.struct.Buffer;

public interface MemoryFactory {

    ChunkSpace newSpace(long memorySize, long saveEvery, Graph graph);

    Buffer newBuffer();

}