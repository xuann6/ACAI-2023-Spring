package acai_lab11.Lab3

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import firrtl.annotations.MemoryLoadFileType

class cacheMem(table_height: Int) extends Module {
  val io = IO(new Bundle {
    // from cache Controller
    val addr = Input(UInt(32.W)) // read-write data
    val wen = Input(Bool()) // Cache Write Enable
    val dataIn = Input(UInt(32.W))
    val Size = Input(UInt(4.W)) 
    val cpuDRAM = Input(Bool())
    val dataOut = Output(UInt(32.W))
    val tagOut = Output(UInt(22.W))
    
    val dm_read = Output(UInt(256.W)) // write back
    val dm_write = Input(UInt(256.W)) // write from dm

  })
  val cacheTable_byte =
    1 + 1 + 17 + 256 // dirty(1) + Valid(1)  + Tag(17) + Data(256)

  val cacheTable = SyncReadMem(table_height, UInt(cacheTable_byte.W))

  val addrTag = WireDefault(io.addr(31, 15))
  val addrIndex = WireDefault(io.addr(14, 5))
  val addrOffset = WireDefault(io.addr(4, 2))
  val addrByteOffset = WireDefault(io.addr(1,0))
  val tableElmt = WireDefault(cacheTable.read(addrIndex))
  val tableTag = WireDefault(tableElmt(52, 32))
  val tableData = WireDefault(tableElmt(31, 0))
  val tableData_segment = WireDefault(0.U(32.W))
  val dataToWrite_segment = Wire(Vec(8,Vec(4, UInt(8.W))))
  val wStrb = WireDefault(0.U)


  for(j <- 0 until 8) {
    when(j.asUInt() === addrOffset){
      tableData_segment := tableElmt(32 * j + 31, 32 * j)
    }
  }
  for(j <- 0 until 4) {
    when(j.asUInt() === addrByteOffset){
      tableData := MuxLookup(io.Size,tableData_segment,Seq(
        "b0000".U -> Cat(Fill(24,tableData_segment(8 * j + 7)),tableData_segment(8 * j + 7, 8 * j)),
        "b0001".U -> Cat(Fill(16,tableData_segment(8 * (j%3) + 15)),tableData_segment(8 * (j%3) + 15, 8 * (j%3))),
        "b0100".U -> Cat(Fill(24,0.U),tableData_segment(8 * j + 7, 8 * j)),
        "b0101".U -> Cat(Fill(16,0.U),tableData_segment(8 * (j%3) + 15, 8 * (j%3))),
       ))
    }
  }
  
  wStrb := MuxLookup(io.Size,"b1111".U,Seq(
    "b0000".U -> "b0001".U,
    "b0001".U -> "b0011".U,
    "b0010".U -> "b1111".U,
  ))

  for(i <- 0 until 8) {
    for(x <- 0 until 4) {
      dataToWrite_segment(i)(x) := tableElmt(i*32 + x * 8 + 7, i*32+ x * 8)
    }    
  }


  List.range(0, 4).map { x =>
    when(wStrb(x) === 1.U) {
      dataToWrite_segment(addrOffset)(addrByteOffset + x.asUInt()) := io.dataIn(x * 8 + 7, x * 8)
    }
  }

  io.dm_read := tableElmt(255,0)

  when(io.wen) { // Write
    when(io.cpuDRAM){
      cacheTable.write(addrIndex, Cat(io.cpuDRAM, true.B, addrTag, dataToWrite_segment.asUInt()))
    }.otherwise{
      cacheTable.write(addrIndex, Cat(io.cpuDRAM, true.B, addrTag, io.dm_write))
    }
    io.dataOut := io.dataIn
  }.otherwise { // Read
    io.dataOut := tableData
  }
  io.tagOut := (tableElmt(274, 256))

}


