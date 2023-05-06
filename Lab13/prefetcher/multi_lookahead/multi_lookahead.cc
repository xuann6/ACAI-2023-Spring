#include <iostream>
#include <iterator>
#include <map>
#include <set>
#include <utility>
#include <vector>

#include "MultiLookahead.hpp"
#include "cache.h"

/* define cache thresh value */
#define THRESH 50

/* define system parameter*/
#define PF_DEGREE 16
#define NUM_UPDATES 500

/* define module size (AMT, ST)*/
#define AMT_SIZE 16
#define BITVEC_SIZE 63
#define ORIGIN 63
#define MAX_OFFSET 63
#define MIN_OFFSET (-1) * MAX_OFFSET
#define NUM_OFFSETS MAX_OFFSET - MIN_OFFSET + 1

MultiLookahead* multi_lookahead;

void CACHE::prefetcher_initialize()
{
  std::cout << NAME << " my multi-lookahead prefetcher" << std::endl;
  multi_lookahead = new MultiLookahead((uint32_t)THRESH, (uint32_t)PF_DEGREE, (uint32_t)NUM_UPDATES, (uint32_t)AMT_SIZE, (uint32_t)BITVEC_SIZE,
                                       (uint32_t)ORIGIN, (int32_t)MAX_OFFSET, (int32_t)MIN_OFFSET, (int32_t)NUM_OFFSETS);
}

uint32_t CACHE::prefetcher_cache_operate(uint64_t addr, uint64_t ip, uint8_t cache_hit, uint8_t type, uint32_t metadata_in)
{
  /* get the information of prefetching */
  std::pair<std::set<uint64_t>, bool> bo_status = multi_lookahead->get_prefetching(addr, (bool)cache_hit);

  /* if status matched, prefetch the data */
  if (bo_status.second) {
    /*
     * Use iterator to prefetch all the value in prefetch history,
     * until the queue of prefetcher is full (prefetch_line function will return value 0)
     */
    for (std::set<uint64_t>::iterator it = bo_status.first.begin(); it != bo_status.first.end(); it++) {

      // cout << endl;
      // cout << "prefetch line: " << addr + (*it << LOG2_BLOCK_SIZE) << endl;

      if (!prefetch_line(addr + (*it << LOG2_BLOCK_SIZE), true, 0)) {
        break;
      }
    }
  }

  /* update the information in access map table */
  multi_lookahead->learning(addr, (bool)cache_hit);

  return metadata_in;
}

uint32_t CACHE::prefetcher_cache_fill(uint64_t addr, uint32_t set, uint32_t way, uint8_t prefetch, uint64_t evicted_addr, uint32_t metadata_in)
{
  multi_lookahead->cache_fill(addr, evicted_addr);
  return metadata_in;
}

void CACHE::prefetcher_cycle_operate() {}

void CACHE::prefetcher_final_stats()
{
  multi_lookahead->get_amt_info();
  multi_lookahead->print_max_socre();
  multi_lookahead->count_tag();
  cout << "Flush count: " << multi_lookahead->count_flush << endl;
  delete multi_lookahead;
}
