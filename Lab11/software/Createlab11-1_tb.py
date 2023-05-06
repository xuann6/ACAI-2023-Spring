def cacheAddr(tag,index):
    return tag *4096 + index *4

# tb Element -> addr     rw wdata cpuDRAM  miss/hit dirty rdata
print(cacheAddr(11,  1), 0, 101,    0,      0,      0,    0)
print(cacheAddr(13,  2), 0, 102,    0,      0,      0,    0)
print(cacheAddr(14,  3), 0, 103,    0,      0,      0,    0)
print(cacheAddr(15,  4), 0, 104,    0,      0,      0,    0)
print(cacheAddr(16,  5), 0, 105,    0,      0,      0,    0)
print(cacheAddr(17,  6), 0, 106,    0,      0,      0,    0)
print(cacheAddr(18,  7), 0, 107,    0,      0,      0,    0)
print(cacheAddr(19,  8), 0, 108,    0,      0,      0,    0)
print(cacheAddr(20,  9), 0, 109,    0,      0,      0,    0)
print(cacheAddr(21, 10), 0, 110,    0,      0,      0,    0)
print(cacheAddr(22, 11), 0, 111,    0,      0,      0,    0)
print(cacheAddr(11,  1), 1,   0,    0,      1,      0,  101) # Read (Hit)
print(cacheAddr(11, 13), 1,   0,    0,      0,      0,    0) # Read (Miss) Cache is Empty
print(cacheAddr(10,  1), 1,   0,    0,      0,      0,    0) # Read (Miss) Cache has different tag.
print(cacheAddr(11,  1), 0, 200,    1,      0,      0,    0) # Write the cache(data not from RAM)
print(cacheAddr(14,  3), 1,   0,    0,      1,      1,  103) # Read (Hit and dirty(1))
print(cacheAddr(19,  8), 0,   0,    0,      0,      0,    0)
print(cacheAddr(20,  9), 1,   0,    0,      1,      0,  109) # Read (Hit)
print(cacheAddr(21, 10), 1,   0,    0,      1,      0,  110) # Read (Hit)
print(cacheAddr(22, 11), 1,   0,    0,      1,      0,  111) # Read (Hit)
