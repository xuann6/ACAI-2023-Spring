#include <algorithm>
#include <bitset>
#include <deque>
#include <iterator>
#include <map>
#include <set>
#include <vector>

#include "cache.h"
#include "math.h"

/* set offset inyo flag */
uint64_t set_bit(uint64_t data, int offset, bool set)
{
  uint64_t temp = 1 << offset;
  return (set) ? data | temp : data & (~temp);
}

/* find the value of "offset" in "data" */
bool find_bit(uint64_t data, int offset) { return (data >> offset) & 1; }

class AccessMapData
{
public:
  uint64_t address;
  uint64_t bit_vector;
  std::deque<int> recent_access;
};

class AccessMapTable
{
private:
  std::vector<AccessMapData> access_map_table;
  size_t size;
  size_t block_size;
  size_t bit_vector_size;
  size_t queue_size;

public:
  AccessMapTable(size_t size, size_t block_size, size_t bit_vector_size, size_t queue_size)
      : block_size(block_size), bit_vector_size(bit_vector_size), queue_size(queue_size), size(size)
  {
    this->access_map_table = std::vector<AccessMapData>(size, {(uint64_t)0, (uint64_t)0});
  }

  bool check_addr(uint64_t addr)
  {
    uint64_t shifted_line_addr = addr >> LOG2_BLOCK_SIZE;

    uint64_t tag = shifted_line_addr / (uint64_t)(bit_vector_size + 1);
    int offset = shifted_line_addr % (uint64_t)(bit_vector_size + 1);

    size_t hash_index = (tag & 0x0F) ^ ((tag & 0xF0) >> 4);
    return this->access_map_table[hash_index].address == addr;
  }

  void insert(uint64_t addr)
  {
    uint64_t shifted_line_addr = addr >> LOG2_BLOCK_SIZE;

    /*
     * find tag and offset:
     *  - tag    : use hash(tag) as access map table entry index
     *  - offset : use offset to modify the number in bit vector of each entry
     */
    uint64_t tag = shifted_line_addr / (uint64_t)(bit_vector_size + 1);
    int offset = shifted_line_addr % (uint64_t)(bit_vector_size + 1);

    /*
     * hash value between 0~15
     */
    size_t hash_index = (tag & 0x0F) ^ ((tag & 0xF0) >> 4);

    /*
     * collect bit vector, queue
     */
    AccessMapData data = access_map_table[hash_index];
    std::deque<int> queue = data.recent_access;
    uint64_t bit_vec = data.bit_vector;

    /*
     * Adjust bit vector, queue
     *    - bit_vec, 64 bits in total:
     *        (MSB) 0 0 0 0 0 0 .... 0 0 0 (LSB)
     *    - queue, PF_DEGREE-1 in total:
     *        (FRONT) 1 3 5 7 ... 9 15 (BACK)
     */
    bit_vec = (1 << offset) | bit_vec;
    queue.push_front(offset);
    if (queue.size() > queue_size)
      queue.pop_back();

    /*
     * Update the value in this entry
     */
    this->access_map_table[hash_index] = {addr, bit_vec, queue};
  }

  void replace_line(uint64_t addr, AccessMapData data)
  {
    uint64_t shifted_line_addr = addr >> LOG2_BLOCK_SIZE;

    uint64_t tag = shifted_line_addr / (uint64_t)(bit_vector_size + 1);
    int offset = shifted_line_addr % (uint64_t)(bit_vector_size + 1);

    /* the index should be replaced */
    size_t hash_index = (tag & 0x0F) ^ ((tag & 0xF0) >> 4);

    this->access_map_table[hash_index] = data;
  }

  AccessMapData get_line(uint64_t addr)
  {
    uint64_t shifted_line_addr = addr >> LOG2_BLOCK_SIZE;

    uint64_t tag = shifted_line_addr / (uint64_t)(bit_vector_size + 1);
    int offset = shifted_line_addr % (uint64_t)(bit_vector_size + 1);

    size_t hash_index = (tag & 0x0F) ^ ((tag & 0xF0) >> 4);

    /* return information if the address is correct, or else return NULL */
    return this->access_map_table[hash_index];
  }

  void get_info()
  {
    /* output basic information */
    cout << endl << endl;
    cout << "Here's the information of AMT table: " << endl
         << " - entry size: " << size << endl
         << " - bit vector length: " << bit_vector_size << endl
         << " - queue length: " << queue_size << endl;
    cout << endl << endl;

    /* output access map table information */
    for (int i = 0; i < size; ++i) {
      cout << std::hex << this->access_map_table[i].address << "     " << std::bitset<64>(access_map_table[i].bit_vector) << "     ";
      for (std::deque<int>::iterator it = this->access_map_table[i].recent_access.begin(); it != this->access_map_table[i].recent_access.end(); it++)
        cout << *it << "-";
      cout << endl;
    }
  }
};

class ScoreTable
{
private:
  std::vector<std::vector<int>> table;
  const int pf_level;

  const int32_t MIN_OFFSET;
  const int32_t MAX_OFFSET;
  const int32_t NUMS_OFFSET;
  const int32_t ORIGIN;
  const int32_t THRESH;

public:
  ScoreTable(size_t pf_level, int32_t min_offset, int32_t max_offset, int32_t nums_offset, int32_t origin, int32_t thresh)
      : pf_level(pf_level), MIN_OFFSET(min_offset), MAX_OFFSET(max_offset), NUMS_OFFSET(nums_offset), ORIGIN(origin), THRESH(thresh)
  {
    this->table = std::vector<std::vector<int>>(pf_level, std::vector<int>(NUMS_OFFSET, 0));
  }

  void reset()
  {
    for (int i = 0; i < pf_level; ++i) {
      for (int j = 0; j < NUMS_OFFSET; ++j) {
        table[i][j] = 0;
      }
    }
  }

  void increase(AccessMapData access_map_data, int offset)
  {
    uint64_t bit_vec = access_map_data.bit_vector;
    std::deque<int> queue = access_map_data.recent_access;

    for (int i = 0; i < pf_level; ++i) {
      /* initialize the last access offset for wach level (without first level) */
      if (i != 0) {
        int idx = queue[i - 1];
        bit_vec = set_bit(bit_vec, idx, (bool)0);
      }

      /* update the score for each level */
      for (int j = 0; j < NUMS_OFFSET; ++j) {
        if (find_bit(bit_vec, j)) {
          int cur_offset = offset - j;
          if (cur_offset >= MIN_OFFSET && cur_offset <= MAX_OFFSET && cur_offset != 0) {
            this->table[i][cur_offset + ORIGIN] += 1;

            /* for recording highest score (TESTING) */
            if (this->table[i][cur_offset + ORIGIN] > max_score)
              max_score = this->table[i][cur_offset + ORIGIN];
          }
        }
      }
    }
  }

  std::vector<std::vector<int>> get_highest_offset()
  {
    std::vector<std::vector<int>> ans(pf_level, std::vector<int>());
    for (int i = 0; i < pf_level; ++i) {
      /* extract the best score in current level */
      int best_score = 0;

      best_score = *max_element(this->table[i].begin(), this->table[i].end());

      /* extract the offset which score == best_score */
      // for (int j = 0; j < NUMS_OFFSET; j++) {
      //   if (table[i][j] == best_score)
      //     ans[i].push_back(j); /* NOTICE: HAVE TO TRANSFER THE INDEX OFFSET INTO OFFSET WITH POSITIVE / NEGATIVE VALUE */
      // }

      for (int j = MIN_OFFSET; j < MAX_OFFSET; j++) {
        if (table[i][j + ORIGIN] == best_score)
          ans[i].push_back(j); /* NOTICE: HAVE TO TRANSFER THE INDEX OFFSET INTO OFFSET WITH POSITIVE / NEGATIVE VALUE */
      }
    }

    return ans;
  }

  /* FOR TESTING */
  int max_score = 0;
};

class MultiLookahead
{
private:
  AccessMapTable* access_map_table;
  ScoreTable* score_table;

  uint64_t round_counter = 0;
  std::set<uint64_t> offset_hist;
  std::set<uint64_t> prefetched_addr;

  const uint32_t THRESH;
  const uint32_t PF_DEGREE;
  const uint32_t NUM_UPDATES;
  const uint32_t AMT_SIZE;
  const uint32_t BITVEC_SIZE;
  const uint32_t ORIGIN;
  const int32_t MAX_OFFSET;
  const int32_t MIN_OFFSET;
  const int32_t NUM_OFFSETS;

  /* FOR TESTING */
  std::set<uint64_t> container;

public:
  /* FOR TESTING */
  int count_flush = 0;

  MultiLookahead(uint32_t THRESH, uint32_t PF_DEGREE, uint32_t NUM_UPDATES, uint32_t AMT_SIZE, uint32_t BITVEC_SIZE, uint32_t ORIGIN, int32_t MAX_OFFSET,
                 int32_t MIN_OFFSET, int32_t NUM_OFFSETS)
      : THRESH(THRESH), PF_DEGREE(PF_DEGREE), NUM_UPDATES(NUM_UPDATES), AMT_SIZE(AMT_SIZE), BITVEC_SIZE(BITVEC_SIZE), ORIGIN(ORIGIN), MAX_OFFSET(MAX_OFFSET),
        MIN_OFFSET(MIN_OFFSET), NUM_OFFSETS(NUM_OFFSETS)
  {
    access_map_table = new AccessMapTable(AMT_SIZE, BLOCK_SIZE, BITVEC_SIZE, PF_DEGREE - 1);
    score_table = new ScoreTable(PF_DEGREE, MIN_OFFSET, MAX_OFFSET, NUM_OFFSETS, ORIGIN, THRESH);
  }

  ~MultiLookahead()
  {
    delete access_map_table;
    delete score_table;
  }

  void clean_offset_hist() { this->offset_hist.clear(); }

  /* FOR TESTING */
  void get_amt_info() { this->access_map_table->get_info(); }
  void print_max_socre() { cout << "max score: " << std::dec << this->score_table->max_score << endl; }
  void count_tag() { cout << "number of entries: " << this->container.size() << endl; }

  std::pair<std::set<uint64_t>, bool> get_prefetching(uint64_t addr, bool cache_hit)
  {
    uint64_t line_addr = (addr >> LOG2_BLOCK_SIZE) << LOG2_BLOCK_SIZE;
    std::pair<std::set<uint64_t>, bool> result(offset_hist, (!cache_hit || prefetched_addr.find(line_addr) != prefetched_addr.end()));

    return result;
  }

  void learning(uint64_t addr, bool cache_hit)
  {
    // cout << "learning start" << endl;
    uint64_t line_addr = (addr >> LOG2_BLOCK_SIZE) << LOG2_BLOCK_SIZE;

    uint64_t tag = (addr >> LOG2_BLOCK_SIZE) / (uint64_t)(BITVEC_SIZE + 1);
    int offset = (addr >> LOG2_BLOCK_SIZE) % (uint64_t)(BITVEC_SIZE + 1);

    // cout << "Round number: " << round_counter << endl;
    // cout << "addr: " << addr << "  line addr: " << line_addr << "  tag: " << tag << "  offset: " << offset << endl;

    if (!cache_hit || prefetched_addr.find(line_addr) != prefetched_addr.end()) {

      /* FOR TESTING (count how many entry we need for access map table) */
      this->container.insert(tag);

      /* check whether the access map table contains the information we want */
      if (access_map_table->check_addr(line_addr)) {
        score_table->increase(access_map_table->get_line(line_addr), offset);
      }
      /* if not, replace the line */
      else {
        /* FOR TESTING */
        count_flush += 1;

        AccessMapData entry = this->access_map_table->get_line(line_addr);
        entry.address = line_addr;
        entry.bit_vector = 0;
        entry.recent_access.clear();

        // cout << "FIND ENTRY FAIL!!" << endl;
        access_map_table->replace_line(line_addr, entry);
      }

      if (round_counter == NUM_UPDATES) {
        /* reset offset history */
        this->clean_offset_hist();

        /* return offset with highest score in each level */
        AccessMapData entry = this->access_map_table->get_line(line_addr);
        std::vector<std::vector<int>> highest_offset = this->score_table->get_highest_offset();

        for (int i = 0; i < PF_DEGREE; ++i) {
          for (int j = 0; j < highest_offset[i].size(); ++j) {

            /* if current offset have beeb prefetched, skip the insert operation */
            int offset_to_prefetch = offset + highest_offset[i][j];

            /* CONDITION: current offset have been accessed */
            if (find_bit(entry.bit_vector, offset_to_prefetch))
              continue;

            /* Get final set of offset (without overlapping offset). */
            offset_hist.insert(highest_offset[i][j]);
            uint64_t new_bit_vec = entry.bit_vector;
            new_bit_vec = set_bit(new_bit_vec, offset_to_prefetch, true);
            /* only modify new bit vector */
            this->access_map_table->replace_line(line_addr, {entry.address, new_bit_vec, entry.recent_access});
          }
        }

        /* reset score table and round counter */
        score_table->reset();
        round_counter = 0;
      } else {
        round_counter += 1;
      }
    }
    // cout << "learning end" << endl;
  }

  void cache_fill(uint64_t addr, uint64_t evicted_addr)
  {
    // cout << "cache fill start" << endl;
    if (prefetched_addr.find(evicted_addr) != prefetched_addr.end()) {
      prefetched_addr.erase(evicted_addr);
    }

    uint64_t issued_line_addr = (addr >> LOG2_BLOCK_SIZE) << LOG2_BLOCK_SIZE;
    access_map_table->insert(issued_line_addr);
    prefetched_addr.insert(addr);
    // cout << "cache fill end" << endl;
  }
};