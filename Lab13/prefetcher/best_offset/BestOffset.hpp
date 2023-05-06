#include <iterator>
#include <map>
#include <set>
#include <vector>

#include "cache.h"

class RecentRequestTable
{
private:
  std::vector<uint64_t> RR_table;

public:
  RecentRequestTable(size_t size) { RecentRequestTable::RR_table = std::vector<uint64_t>(size, (uint64_t)0); }

  bool check_addr(uint64_t line_addr)
  {
    uint64_t shifted_line_addr = line_addr >> LOG2_BLOCK_SIZE;
    size_t hash_index = (shifted_line_addr & 0xFF) ^ ((shifted_line_addr & 0xFF00) >> 8);
    return RecentRequestTable::RR_table[hash_index] == ((shifted_line_addr & 0xFFF00) >> 8);
  }

  void insert(uint64_t line_addr)
  {
    uint64_t shifted_line_addr = line_addr >> LOG2_BLOCK_SIZE;
    size_t hash_index = (shifted_line_addr & 0xFF) ^ ((shifted_line_addr & 0xFF00) >> 8);
    RecentRequestTable::RR_table[hash_index] = ((shifted_line_addr & 0xFFF00) >> 8);
  }
};

class ScoreTable
{
private:
  std::map<uint64_t, uint8_t> table;
  const std::vector<uint64_t> OFFSET_LIST;

public:
  ScoreTable(std::vector<uint64_t> offset_list) : OFFSET_LIST(offset_list) { ScoreTable::reset(); }

  void reset()
  {
    for (size_t i = 0; i < ScoreTable::OFFSET_LIST.size(); ++i) {
      ScoreTable::table[ScoreTable::OFFSET_LIST[i]] = (uint8_t)0;
    }
  }

  void increase(uint64_t offset) { table[offset] += 1; }

  uint8_t get_score(uint64_t offset) { return table[offset]; }

  std::pair<uint64_t, uint8_t> get_highest_offset()
  {
    uint64_t offset = 0;
    uint8_t highest_score = 0;

    for (size_t i = 0; i < ScoreTable::OFFSET_LIST.size(); ++i) {
      offset = (ScoreTable::table[ScoreTable::OFFSET_LIST[i]] > highest_score) ? ScoreTable::OFFSET_LIST[i] : offset;
      highest_score = (ScoreTable::table[ScoreTable::OFFSET_LIST[i]] > highest_score) ? ScoreTable::table[ScoreTable::OFFSET_LIST[i]] : highest_score;
    }

    return std::pair<uint64_t, uint8_t>(offset, highest_score);
  }
};

class BestOffset
{
private:
  RecentRequestTable* RR_table;
  ScoreTable* score_table;

  uint64_t counter = 0;
  uint64_t offset = 0;
  std::set<uint64_t> prefetched_addr;
  std::map<int, int> offset_history;

  const uint8_t BADSCORE;
  const uint8_t SCOREMAX;
  const size_t ROUNDMAX;
  const std::vector<uint64_t> OFFSET_LIST;

public:
  BestOffset(uint8_t badscore, uint8_t scoremax, size_t roundmax, std::vector<uint64_t> offset_list, size_t RR_size)
      : BADSCORE(badscore), SCOREMAX(scoremax), ROUNDMAX(roundmax), OFFSET_LIST(offset_list)
  {
    RR_table = new RecentRequestTable(RR_size);
    score_table = new ScoreTable(offset_list);
  }

  ~BestOffset()
  {
    delete RR_table;
    delete score_table;
  }

  std::pair<uint64_t, bool> get_prefetching(uint64_t addr, bool cache_hit)
  {
    uint64_t line_addr = (addr >> LOG2_BLOCK_SIZE) << LOG2_BLOCK_SIZE;
    std::pair<uint64_t, bool> result(offset, (offset > 0 && (!cache_hit || prefetched_addr.find(line_addr) != prefetched_addr.end())));
    if (result.second) {
      offset_history[offset] = (offset_history.count(offset)) ? offset_history[offset] + 1 : 1;
    }
    return result;
  }

  void learning(uint64_t addr, bool cache_hit)
  {
    uint64_t line_addr = (addr >> LOG2_BLOCK_SIZE) << LOG2_BLOCK_SIZE;

    if (!cache_hit || prefetched_addr.find(line_addr) != prefetched_addr.end()) {
      uint64_t test_line_addr = line_addr - (OFFSET_LIST[counter % OFFSET_LIST.size()] << LOG2_BLOCK_SIZE);

      if (RR_table->check_addr(test_line_addr)) {
        score_table->increase(OFFSET_LIST[counter % OFFSET_LIST.size()]);
      }

      if (counter == OFFSET_LIST.size() * ROUNDMAX - 1 || score_table->get_score(OFFSET_LIST[counter % OFFSET_LIST.size()]) == SCOREMAX) {
        std::pair<uint64_t, uint8_t> highest_offset_score = score_table->get_highest_offset();
        offset = (highest_offset_score.second > BADSCORE) ? highest_offset_score.first : 0;
        score_table->reset();
        counter = 0;
      } else {
        counter += 1;
      }
    }
  }

  void cache_fill(uint64_t addr, uint64_t evicted_addr)
  {
    if (prefetched_addr.find(evicted_addr) != prefetched_addr.end()) {
      prefetched_addr.erase(evicted_addr);
    }

    uint64_t issued_line_addr = ((addr >> LOG2_BLOCK_SIZE) - offset) << LOG2_BLOCK_SIZE;
    RR_table->insert(issued_line_addr);
    prefetched_addr.insert(addr);
  }
};
