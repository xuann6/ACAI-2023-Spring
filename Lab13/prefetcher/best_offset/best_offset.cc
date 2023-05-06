#include <iostream>
#include <iterator>
#include <map>
#include <set>
#include <vector>

#include "BestOffset.hpp"
#include "cache.h"

#define BADSCORE 1
#define SCOREMAX 31
#define ROUNDMAX 100
#define RR_TABLE_SIZE 256

const std::vector<uint64_t> OFFSET_LIST = {1,   2,   3,   4,   5,   6,   8,   9,   10,  12,  15,  16,  18,  20,  24,  25,  27,  30,
                                           32,  36,  40,  45,  48,  50,  54,  60,  64,  72,  75,  80,  81,  90,  96,  100, 108, 120,
                                           125, 128, 135, 144, 150, 160, 162, 180, 192, 200, 216, 225, 240, 243, 250, 256};

BestOffset* best_offset;

void CACHE::prefetcher_initialize()
{
  std::cout << NAME << " my best-offset prefetcher" << std::endl;
  best_offset = new BestOffset((uint8_t)BADSCORE, (uint8_t)SCOREMAX, (size_t)ROUNDMAX, OFFSET_LIST, (size_t)RR_TABLE_SIZE);
}

uint32_t CACHE::prefetcher_cache_operate(uint64_t addr, uint64_t ip, uint8_t cache_hit, uint8_t type, uint32_t metadata_in)
{
  std::pair<uint64_t, bool> bo_status = best_offset->get_prefetching(addr, (bool)cache_hit);

  if (bo_status.second) {
    // std::cout << "best_offset prefetch line: " << addr + (bo_status.first << LOG2_BLOCK_SIZE) << endl;
    prefetch_line(addr + (bo_status.first << LOG2_BLOCK_SIZE), true, 0);
  }

  best_offset->learning(addr, (bool)cache_hit);

  return metadata_in;
}

uint32_t CACHE::prefetcher_cache_fill(uint64_t addr, uint32_t set, uint32_t way, uint8_t prefetch, uint64_t evicted_addr, uint32_t metadata_in)
{
  best_offset->cache_fill(addr, evicted_addr);

  return metadata_in;
}

void CACHE::prefetcher_cycle_operate() {}

void CACHE::prefetcher_final_stats()
{
  // std::cout << std::endl << "BO Prefetcher History:" << std::endl;
  // for (std::map<int, int>::iterator it = offset_history.begin(); it != offset_history.end(); ++it) {
  //   std::cout << it->first << " : " << it->second << ", ";
  // }
  // std::cout << std::endl;
  delete best_offset;
}
