/**
 * Copyright 2017 The GreyCat Authors.  All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package greycat.internal.heap;

import greycat.Constants;
import greycat.chunk.*;
import greycat.internal.CoreConstants;
import greycat.struct.*;
import greycat.utility.Base64;
import greycat.utility.HashHelper;
import greycat.utility.Tuple;

class HeapTimeTreeDValueChunk implements TimeTreeDValueChunk {

    //constants definition
    private static final int META_SIZE = 3;

    private final long _index;
    private final HeapChunkSpace _space;

    private int _root = -1;
    private int[] _back_meta;
    long[] _k;
    double[] _values;
    boolean[] _values_is_null;
    private boolean[] _colors;

    private long _hash;
    private boolean _inSync;

    private volatile long _capacity;
    private volatile long _magic;
    private volatile int _size;

    private long _max;
    private int _group;

    HeapTimeTreeDValueChunk(final HeapChunkSpace p_space, final long p_index) {
        _space = p_space;
        _index = p_index;
        _magic = 0;
        _hash = 0;
        _capacity = 0;
        _size = 0;
        _inSync = true;
        _group = 0;
    }

    @Override
    public final long capacity() {
        return _capacity;
    }

    @Override
    public final void setCapacity(long v) {
        _capacity = v;
    }

    @Override
    public long max() {
        return _max;
    }

    @Override
    public final long world() {
        return _space.worldByIndex(_index);
    }

    @Override
    public final long time() {
        return _space.timeByIndex(_index);
    }

    @Override
    public final long id() {
        return _space.idByIndex(_index);
    }

    @Override
    public final int size() {
        return _size;
    }

    @Override
    public synchronized final boolean inSync() {
        return _inSync;
    }

    @Override
    public synchronized final boolean sync(long remoteHash) {
        if (_inSync && remoteHash != _hash) {
            _inSync = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public final int group() {
        return _group;
    }

    @Override
    public final Chunk setGroup(int g) {
        _group = g;
        return this;
    }

    @Override
    public synchronized final void save(Buffer buffer) {
        final long beginIndex = buffer.writeIndex();
        if (_group != 0) {
            Base64.encodeIntToBuffer(_group, buffer);
            buffer.write(CoreConstants.CHUNK_META_SEP);
        }
        Base64.encodeIntToBuffer(_size, buffer);
        buffer.write(CoreConstants.CHUNK_SEP);
        Base64.encodeLongToBuffer(_capacity, buffer);
        buffer.write(CoreConstants.CHUNK_SEP);
        for (int i = 0; i < _size; i++) {
            Base64.encodeLongToBuffer(this._k[i], buffer);
            buffer.write(CoreConstants.CHUNK_VAL_SEP);
            if (!this._values_is_null[i]) {
                Base64.encodeDoubleToBuffer(this._values[i], buffer);
            }
            buffer.write(CoreConstants.CHUNK_VAL_SEP);
        }
        _hash = HashHelper.hashBuffer(buffer, beginIndex, buffer.writeIndex());
    }

    @Override
    public final synchronized void saveDiff(Buffer buffer) {
        if (_hash == Constants.EMPTY_HASH) {
            final long beginIndex = buffer.writeIndex();
            Base64.encodeLongToBuffer(_capacity, buffer);
            buffer.write(CoreConstants.CHUNK_SEP);
            Base64.encodeIntToBuffer(_size, buffer);
            for (int i = 0; i < _size; i++) {
                /*if (_diff[i]) {
                    buffer.write(CoreConstants.CHUNK_VAL_SEP);
                    Base64.encodeLongToBuffer(this._k[i], buffer);
                }*/
            }
            _hash = HashHelper.hashBuffer(buffer, beginIndex, buffer.writeIndex());
            // CoreConstants.fillBooleanArray(_diff, false);
        }
    }

    @Override
    public final synchronized void load(final Buffer buffer) {
        internal_load(buffer, true);
        //TODO reset _dirty
    }

    @Override
    public final synchronized void loadDiff(final Buffer buffer) {
        if (internal_load(buffer, false) && _hash != Constants.EMPTY_HASH) {
            _hash = Constants.EMPTY_HASH;
            if (_space != null) {
                _space.notifyUpdate(_index);
            }
        }
    }

    @Override
    public final long hash() {
        return _hash;
    }

    private boolean internal_load(final Buffer buffer, final boolean initial) {
        if (buffer == null || buffer.length() == 0) {
            return false;
        }
        boolean isDirty = false;
        long cursor = 0;
        long previous = 0;
        long payloadSize = buffer.length();
        int extraCursor = 0;

        boolean waiting_value = false;
        long key_time = -1;
        while (cursor < payloadSize) {
            final byte current = buffer.read(cursor);
            switch (current) {
                case Constants.CHUNK_META_SEP:
                    if (previous != cursor) {
                        _group = Base64.decodeToIntWithBounds(buffer, previous, cursor);
                    }
                    previous = cursor + 1;
                    break;
                case Constants.CHUNK_SEP:
                    switch (extraCursor) {
                        case 0:
                            final int treeSize = Base64.decodeToIntWithBounds(buffer, previous, cursor);
                            final int closePowerOfTwo = (int) Math.pow(2, Math.ceil(Math.log(treeSize) / Math.log(2)));
                            reallocate(closePowerOfTwo);
                            extraCursor++;
                            break;
                        case 1:
                            _capacity = Base64.decodeToLongWithBounds(buffer, previous, cursor);
                            break;
                    }
                    previous = cursor + 1;
                    break;
                case Constants.CHUNK_VAL_SEP:
                    if (waiting_value) {
                        final Tuple<Boolean, Integer> index;
                        if (previous == cursor) {
                            index = internal_insert(key_time, 0d, true);
                        } else {
                            index = internal_insert(key_time, Base64.decodeToDoubleWithBounds(buffer, previous, cursor), false);
                        }
                        isDirty = isDirty || index.left();
                        waiting_value = false;
                    } else {
                        key_time = Base64.decodeToLongWithBounds(buffer, previous, cursor);
                        waiting_value = true;
                    }
                    previous = cursor + 1;
                    break;
            }
            cursor++;
        }
        return isDirty;
    }

    @Override
    public final long index() {
        return _index;
    }

    @Override
    public synchronized long previous(final long key) {
        //lock and load fromVar main memory
        long resultKey;
        int result = internal_previous_index(key);
        if (result != -1) {
            resultKey = key(result);
        } else {
            resultKey = CoreConstants.NULL_LONG;
        }
        return resultKey;
    }

    @Override
    public synchronized int previousOffset(final long key) {
        return internal_previous_index(key);
    }

    @Override
    public final Double getValue(int offset) {
        if (this._values_is_null[offset]) {
            return null;
        } else {
            return _values[offset];
        }
    }

    @Override
    public final long getKey(int offset) {
        return _k[offset];
    }

    @Override
    public StateChunk state(final int offset) {
        return new MockNodeStateDValue(this, offset);
    }

    @Override
    public synchronized long next(long key) {
        long resultKey;
        int result = internal_previousOrEqual_index(key);
        if (result != -1) {
            result = internal_next(result);
        }
        if (result != -1) {
            resultKey = key(result);
        } else {
            resultKey = CoreConstants.NULL_LONG;
        }
        return resultKey;
    }

    @Override
    public synchronized final long previousOrEqual(long key) {
        long resultKey;
        int result = internal_previousOrEqual_index(key);

        if (result != -1) {
            resultKey = key(result);
        } else {
            resultKey = CoreConstants.NULL_LONG;
        }
        return resultKey;
    }

    @Override
    public int previousOrEqualOffset(long key) {
        return internal_previousOrEqual_index(key);
    }

    @Override
    public final long magic() {
        return this._magic;
    }

    @Override
    public synchronized final int insert(final long p_key) {
        final Tuple<Boolean, Integer> index = internal_insert(p_key, 0.0d, false);
        if (index.left()) {
            internal_set_dirty();
            return index.right();
        } else {
            return index.right();
        }
    }

    @Override
    public final byte chunkType() {
        return ChunkType.TIME_TREE_DVALUE_CHUNK;
    }

    private void reallocate(int newCapacity) {
        if (_k != null && newCapacity <= _k.length) {
            return;
        }
        long[] new_back_kv = new long[newCapacity];
        if (_k != null) {
            System.arraycopy(_k, 0, new_back_kv, 0, _size);
        }
        double[] new_back_value = new double[newCapacity];
        if (_values != null) {
            System.arraycopy(_values, 0, new_back_value, 0, _size);
        }
        boolean[] new_back_value_is_null = new boolean[newCapacity];
        if (_values_is_null != null) {
            System.arraycopy(_values_is_null, 0, new_back_value_is_null, 0, _size);
        }
        boolean[] new_back_diff = new boolean[newCapacity];
        CoreConstants.fillBooleanArray(new_back_diff, false);
        boolean[] new_back_colors = new boolean[newCapacity];
        if (_colors != null) {
            System.arraycopy(_colors, 0, new_back_colors, 0, _size);
            for (int i = _size; i < newCapacity; i++) {
                new_back_colors[i] = false;
            }
        }
        int[] new_back_meta = new int[newCapacity * META_SIZE];
        if (_back_meta != null) {
            System.arraycopy(_back_meta, 0, new_back_meta, 0, _size * META_SIZE);
            for (int i = _size * META_SIZE; i < newCapacity * META_SIZE; i++) {
                new_back_meta[i] = -1;
            }
        }
        _back_meta = new_back_meta;
        _k = new_back_kv;
        _values = new_back_value;
        _values_is_null = new_back_value_is_null;
        _colors = new_back_colors;
    }

    private long key(int p_currentIndex) {
        if (p_currentIndex == -1) {
            return -1;
        }
        return _k[p_currentIndex];
    }

    private int left(int p_currentIndex) {
        if (p_currentIndex == -1) {
            return -1;
        }
        return _back_meta[p_currentIndex * META_SIZE];
    }

    private void setLeft(int p_currentIndex, int p_paramIndex) {
        _back_meta[p_currentIndex * META_SIZE] = p_paramIndex;
    }

    private int right(int p_currentIndex) {
        if (p_currentIndex == -1) {
            return -1;
        }
        return _back_meta[(p_currentIndex * META_SIZE) + 1];
    }

    private void setRight(int p_currentIndex, int p_paramIndex) {
        _back_meta[(p_currentIndex * META_SIZE) + 1] = p_paramIndex;
    }

    private int parent(int p_currentIndex) {
        if (p_currentIndex == -1) {
            return -1;
        }
        return _back_meta[(p_currentIndex * META_SIZE) + 2];
    }

    private void setParent(int p_currentIndex, int p_paramIndex) {
        _back_meta[(p_currentIndex * META_SIZE) + 2] = p_paramIndex;
    }

    private boolean color(int p_currentIndex) {
        if (p_currentIndex == -1) {
            return true;
        }
        return _colors[p_currentIndex];
    }

    private void setColor(int p_currentIndex, boolean p_paramIndex) {
        _colors[p_currentIndex] = p_paramIndex;
    }

    private int grandParent(int p_currentIndex) {
        if (p_currentIndex == -1) {
            return -1;
        }
        if (parent(p_currentIndex) != -1) {
            return parent(parent(p_currentIndex));
        } else {
            return -1;
        }
    }

    private int sibling(int p_currentIndex) {
        if (parent(p_currentIndex) == -1) {
            return -1;
        } else {
            if (p_currentIndex == left(parent(p_currentIndex))) {
                return right(parent(p_currentIndex));
            } else {
                return left(parent(p_currentIndex));
            }
        }
    }

    private int uncle(int p_currentIndex) {
        if (parent(p_currentIndex) != -1) {
            return sibling(parent(p_currentIndex));
        } else {
            return -1;
        }
    }

    private int internal_previous(int p_index) {
        int p = p_index;
        if (left(p) != -1) {
            p = left(p);
            while (right(p) != -1) {
                p = right(p);
            }
            return p;
        } else {
            if (parent(p) != -1) {
                if (p == right(parent(p))) {
                    return parent(p);
                } else {
                    while (parent(p) != -1 && p == left(parent(p))) {
                        p = parent(p);
                    }
                    return parent(p);
                }
            } else {
                return -1;
            }
        }
    }

    private int internal_next(int p_index) {
        int p = p_index;
        if (right(p) != -1) {
            p = right(p);
            while (left(p) != -1) {
                p = left(p);
            }
            return p;
        } else {
            if (parent(p) != -1) {
                if (p == left(parent(p))) {
                    return parent(p);
                } else {
                    while (parent(p) != -1 && p == right(parent(p))) {
                        p = parent(p);
                    }
                    return parent(p);
                }

            } else {
                return -1;
            }
        }
    }

    /*
    private long lookup(long p_key) {
        int n = _root;
        if (n == -1) {
            return CoreConstants.NULL_LONG;
        }
        while (n != -1) {
            if (p_key == key(n)) {
                return key(n);
            } else {
                if (p_key < key(n)) {
                    n = left(n);
                } else {
                    n = right(n);
                }
            }
        }
        return n;
    }
    */


    private int internal_previousOrEqual_index(long p_key) {
        int p = _root;
        if (p == -1) {
            return p;
        }
        while (p != -1) {
            if (p_key == key(p)) {
                return p;
            }
            if (p_key > key(p)) {
                if (right(p) != -1) {
                    p = right(p);
                } else {
                    return p;
                }
            } else {
                if (left(p) != -1) {
                    p = left(p);
                } else {
                    int parent = parent(p);
                    long ch = p;
                    while (parent != -1 && ch == left(parent)) {
                        ch = parent;
                        parent = parent(parent);
                    }
                    return parent;
                }
            }
        }
        return -1;
    }

    private int internal_previous_index(long p_key) {
        int p = _root;
        if (p == -1) {
            return p;
        }
        while (p != -1) {
            if (p_key > key(p)) {
                if (right(p) != -1) {
                    p = right(p);
                } else {
                    return p;
                }
            } else {
                if (left(p) != -1) {
                    p = left(p);
                } else {
                    int parent = parent(p);
                    long ch = p;
                    while (parent != -1 && ch == left(parent)) {
                        ch = parent;
                        parent = parent(parent);
                    }
                    return parent;
                }
            }
        }
        return -1;
    }

    private void rotateLeft(int n) {
        int child = right(n);
        setRight(n, left(child));
        if (left(child) != -1) {
            setParent(left(child), n);
        }
        setParent(child, parent(n));
        if (n == _root) {
            _root = child;
        } else {
            if (n == left(parent(n))) {
                setLeft(parent(n), child);
            } else {
                setRight(parent(n), child);
            }
        }
        setLeft(child, n);
        setParent(n, child);
    }

    private void rotateRight(int n) {
        int child = left(n);
        setLeft(n, right(child));
        if (right(child) != -1) {
            setParent(right(child), n);
        }
        setParent(child, parent(n));
        if (n == _root) {
            _root = child;
        } else {
            if (n == left(parent(n))) {
                setLeft(parent(n), child);
            } else {
                setRight(parent(n), child);
            }
        }
        setRight(child, n);
        setParent(n, child);
    }

    @Override
    public final void insertValue(long p_key, Double p_value) {
        final Tuple<Boolean, Integer> inserted;
        if (p_value == null) {
            inserted = internal_insert(p_key, 0d, true);
        } else {
            inserted = internal_insert(p_key, p_value, false);
        }
        if (inserted.left()) {
            internal_set_dirty();
        }
    }

    @SuppressWarnings("Duplicates")
    private Tuple<Boolean, Integer> internal_insert(long p_key, double value, boolean isNull) {
        if (p_key > _max) {
            _max = p_key;
        }
        if (_k == null || _k.length == _size) {
            int length = _size;
            if (length == 0) {
                length = Constants.MAP_INITIAL_CAPACITY;
            } else {
                length = length * 2;
            }
            reallocate(length);
        }
        int newIndex = _size;
        if (newIndex == 0) {
            _root = newIndex;
            _k[newIndex] = p_key;
            _values[newIndex] = value;
            _values_is_null[newIndex] = isNull;
            setLeft(newIndex, -1);
            setRight(newIndex, -1);
            setColor(newIndex, true);
            setParent(newIndex, -1);
        } else {
            int father = -1;
            int leaf = _root;
            boolean left = false;
            while (leaf != -1) {
                father = leaf;
                if (_k[father] == p_key) {
                    if (_values[father] == value && _values_is_null[father] == isNull) {
                        return new Tuple<Boolean, Integer>(false, father);
                    } else {
                        _values[father] = value;
                        _values_is_null[father] = isNull;
                        return new Tuple<Boolean, Integer>(true, father);
                    }
                }
                if (key(father) < p_key) {
                    leaf = right(father);
                    left = false;
                } else {
                    leaf = left(father);
                    left = true;
                }
            }
            setColor(newIndex, false);
            _k[newIndex] = p_key;
            _values[newIndex] = value;
            _values_is_null[newIndex] = isNull;
            setLeft(newIndex, -1);
            setRight(newIndex, -1);
            setParent(newIndex, father);
            if (left) {
                setLeft(father, newIndex);
            } else {
                setRight(father, newIndex);
            }

            int nodeStudy = newIndex;
            while (father != -1 && !color(father)) {
                int greatFather = parent(father);
                int uncle = uncle(nodeStudy);
                if (!color(uncle)) {
                    setColor(father, true);
                    setColor(uncle, true);
                    setColor(greatFather, false);
                    nodeStudy = greatFather;
                    father = parent(nodeStudy);
                } else {
                    if (father == left(greatFather)) {
                        if (nodeStudy == right(father)) {
                            nodeStudy = father;
                            rotateLeft(nodeStudy);
                            father = parent(nodeStudy);
                            greatFather = parent(father);
                        }
                        setColor(father, true);
                        setColor(greatFather, false);
                        rotateRight(greatFather);
                    } else {
                        if (nodeStudy == left(father)) {
                            nodeStudy = father;
                            rotateRight(nodeStudy);
                            father = parent(nodeStudy);
                            greatFather = parent(father);
                        }
                        setColor(father, true);
                        setColor(greatFather, false);
                        rotateLeft(greatFather);
                    }
                }
            }
            setColor(_root, true);
        }
        _size++;
        return new Tuple<Boolean, Integer>(true, _size - 1);
    }

    private void internal_set_dirty() {
        _magic = _magic + 1;
        if (_space != null && _hash != Constants.EMPTY_HASH) {
            _hash = Constants.EMPTY_HASH;
            _space.notifyUpdate(_index);
        }
    }

    @Override
    public void rangeValue(long startKey, long endKey, long maxElements, TreeDValueWalker walker) {
        //lock and load fromVar main memory
        int nbElements = 0;
        int indexEnd = internal_previousOrEqual_index(endKey);
        while (indexEnd != -1 && key(indexEnd) >= startKey && nbElements < maxElements) {
            if (_values_is_null[indexEnd]) {
                walker.elem(_k[indexEnd], null);
            } else {
                walker.elem(_k[indexEnd], _values[indexEnd]);
            }
            nbElements++;
            indexEnd = internal_previous(indexEnd);
        }
    }

    @Override
    public synchronized final void range(final long startKey, final long endKey, final long maxElements, final TreeWalker walker) {
        //lock and load fromVar main memory
        int nbElements = 0;
        int indexEnd = internal_previousOrEqual_index(endKey);
        while (indexEnd != -1 && key(indexEnd) >= startKey && nbElements < maxElements) {
            walker.elem(key(indexEnd));
            nbElements++;
            indexEnd = internal_previous(indexEnd);
        }
    }

}
