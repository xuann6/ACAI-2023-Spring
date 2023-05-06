package acai_lab11.Lab4

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import firrtl.annotations.MemoryLoadFileType

class cacheMem(val table_height: Int, val way: Int) extends Module {
  val io = IO(new Bundle {
    // from cache Controller
    val addr = Input(UInt(32.W)) // read-write data
    val wen = Input(Bool()) // Cache Write Enable
    val dataIn = Input(UInt(32.W))
    val Size = Input(UInt(4.W)) 
    val chosenWay = Input(UInt(1.W))

    val cpuDRAM = Input(Bool())
    val dataOut = Output(UInt(32.W))
    val tagOut = Output(Vec(way,UInt(20.W)))
    val dm_read = Output(UInt(256.W)) // write back
    val dm_write = Input(UInt(256.W)) // write from dm
  })
  io.dataOut := (0.U(32.W))
  val cacheTable_bit =
    1 + 1 + 18 + 256 // dirty(1) + Valid(1)  + Tag(18) + Data(256)

  val cacheTable = SyncReadMem(table_height / way, Vec(way, UInt(cacheTable_bit.W)))


  val addrTag = WireDefault(io.addr(31, 14))
  val addrIndex = WireDefault(io.addr(13, 5))
  val addrOffset = WireDefault(io.addr(4, 2))
  val addrByteOffset = WireDefault(io.addr(1,0))

  val tableElmt = WireDefault(cacheTable.read(addrIndex))
  val tableData = WireDefault(VecInit(Seq.fill(way)(0.U(32.W))))
  val dataToWrite_segment = Wire(Vec(8,Vec(4, UInt(8.W))))
  val tableData_segment = WireDefault(VecInit(Seq.fill(way)(0.U(32.W))))
  val wStrb = WireDefault(0.U(4.W))


  for (i <- 0 until way) {
    for(j <- 0 until 8) {
      when(j.asUInt() === addrOffset){
        tableData_segment(i) := tableElmt(i)(32 * j + 31, 32 * j)
      }
    }
  
    for(j <- 0 until 4) {
      when(j.asUInt() === addrByteOffset){
        tableData(i) := MuxLookup(io.Size,tableData_segment(i),Seq(
          "b0000".U -> Cat(Fill(24,tableData_segment(i)(8 * j + 7)),tableData_segment(i)(8 * j + 7, 8 * j)),
          "b0001".U -> Cat(Fill(16,tableData_segment(i)(8 * (j%3) + 15)),tableData_segment(i)(8 * (j%3) + 15, 8 * (j%3))),
          "b0100".U -> Cat(Fill(24,0.U),tableData_segment(i)(8 * j + 7, 8 * j)),
          "b0101".U -> Cat(Fill(16,0.U),tableData_segment(i)(8 * (j%3) + 15, 8 * (j%3))),
        ))
      }
    }
  }
  
  wStrb := MuxLookup(io.Size,"b1111".U,Seq(
    "b0000".U -> "b0001".U,
    "b0001".U -> "b0011".U,
    "b0010".U -> "b1111".U,
  ))

  
  for(i <- 0 until 8) {
    for(x <- 0 until 4) {
      dataToWrite_segment(i)(x) := tableElmt(io.chosenWay)(i*32 + x * 8 + 7, i*32+ x * 8)
    }    
  }

  
  List.range(0, 4).map { x =>
    when(wStrb(x) === 1.U) {
      dataToWrite_segment(addrOffset)(addrByteOffset + x.asUInt()) := io.dataIn(x * 8 + 7, x * 8)
    }
  }

  io.dm_read := tableElmt(io.chosenWay)(255,0)

  // cpuDRAM : determine data is from cpu or dram (miss or hit)

  when(io.wen) {
    // choose way 1
    when(io.chosenWay === 0.U) {
      when(io.cpuDRAM){
        cacheTable.write(
          addrIndex,
          VecInit(
            Cat(io.cpuDRAM, 1.U, addrTag, dataToWrite_segment.asUInt()),
            Cat(io.cpuDRAM, 1.U, addrTag, dataToWrite_segment.asUInt())
          ),
          Seq(true.B, false.B) // write function mask, 2 way, list length = 2
        )
      }.otherwise{
        cacheTable.write(
          addrIndex,
          VecInit(
            Cat(0.U, 1.U, addrTag, io.dm_write),
            Cat(0.U, 1.U, addrTag, io.dm_write)
          ),
          Seq(true.B, false.B)
        )
      }
    // choose way 2
    }.otherwise {
      when(io.cpuDRAM){
        cacheTable.write(
          addrIndex,
          VecInit(
            Cat(io.cpuDRAM, 1.U, addrTag, dataToWrite_segment.asUInt()),
            Cat(io.cpuDRAM, 1.U, addrTag, dataToWrite_segment.asUInt())
          ),
          Seq(false.B, true.B)
        )
      }.otherwise{
        cacheTable.write(
          addrIndex,
          VecInit(
            Cat(0.U, 1.U, addrTag, io.dm_write),
            Cat(0.U, 1.U, addrTag, io.dm_write)
          ),
          Seq(false.B, true.B)
        )
      }
    }
  }.otherwise { 
      io.dataOut := tableData(io.chosenWay)  
  }
  for(i <- 0 until way){
    io.tagOut(i) := tableElmt(i)(275,256) 
  }
}

