package lab10.AXI

import chisel3._
import chisel3.util._

class writeMaster(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val writeAddr = Flipped(Decoupled(new AXIAddress(addrWidth)))
  val writeData = Flipped(Decoupled(new AXIWriteData(dataWidth)))
  val writeResp = Decoupled(UInt(2.W))
}
class writeSlave(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val writeAddr = Decoupled(new AXIAddress(addrWidth))
  val writeData = Decoupled(new AXIWriteData(dataWidth))
  val writeResp = Flipped(Decoupled(UInt(2.W)))
}

class AXIWriteBus(val mSlaves: Int, val addrWidth: Int, val dataWidth: Int, val addrMap: List[(UInt, UInt)]) extends Module {
  val io = IO(new Bundle {
    val master = new writeMaster(addrWidth, dataWidth)
    val slave = Vec(mSlaves, new writeSlave(addrWidth, dataWidth))
  })
  val write_port = WireDefault(0.U(1.W))
  val write_port_reg = RegInit(0.U(1.W))
  val write_addr_reg = RegInit(0.U((addrWidth).W))
  val write_addr_reg_len = RegInit(0.U(8.W))
  val write_addr_reg_size = RegInit(0.U(3.W))
  val write_addr_reg_burst = RegInit(0.U(2.W))
  val write_addr_reg_valid = RegInit(false.B)
  val write_data_reg = RegInit(0.U((dataWidth).W))
  val write_data_reg_valid = RegInit(false.B)
  val write_data_reg_strb = RegInit(0.U((dataWidth / 8).W))
  val write_data_reg_last = RegInit(false.B)
  val slave_write_startAddr = Wire(Vec(mSlaves, UInt(addrWidth.W)))
  val slave_write_endAddr = Wire(Vec(mSlaves, UInt(addrWidth.W)))

  for (i <- 0 until addrMap.length) {
    slave_write_startAddr(i) := addrMap(i)._1
    slave_write_endAddr(i) := addrMap(i)._2
  }

  for (i <- 0 until mSlaves) {
    io.slave(i).writeData.valid := false.B
    io.slave(i).writeData.bits.data := 0.U
    io.slave(i).writeData.bits.strb := 0.U
    io.slave(i).writeData.bits.last := false.B
    io.slave(i).writeAddr.valid := false.B
    io.slave(i).writeAddr.bits.addr := 0.U
    io.slave(i).writeAddr.bits.len := 0.U
    io.slave(i).writeAddr.bits.size := 0.U
    io.slave(i).writeAddr.bits.burst := 0.U
    io.slave(i).writeResp.ready := false.B

    when(slave_write_startAddr(i) <= io.master.writeAddr.bits.addr && io.master.writeAddr.bits.addr < slave_write_endAddr(i)) {
      write_port := i.U // 找出slave的port
    }
  }
  io.master.writeData.ready := false.B
  io.master.writeAddr.ready := false.B
  io.master.writeResp.valid := false.B
  io.master.writeResp.bits := 0.U

  when(io.master.writeAddr.valid && write_addr_reg_valid === false.B) {
    write_port_reg := write_port
    write_addr_reg := io.master.writeAddr.bits.addr
    write_addr_reg_len := io.master.writeAddr.bits.len
    write_addr_reg_size := io.master.writeAddr.bits.size
    write_addr_reg_burst := io.master.writeAddr.bits.burst
    write_addr_reg_valid := true.B
  }.otherwise {
    write_addr_reg := write_addr_reg
    write_addr_reg_len := write_addr_reg_len
    write_addr_reg_size := write_addr_reg_size
    write_addr_reg_burst := write_addr_reg_burst
    write_addr_reg_valid := write_addr_reg_valid
  }

  when(write_addr_reg_valid) {
    io.master.writeAddr.ready := false.B
  }.otherwise {
    io.master.writeAddr.ready := true.B
  }

  when(io.master.writeData.valid /*(Hsuan) */ /*&& write_data_reg_valid === false.B*/) {
    write_data_reg_strb := io.master.writeData.bits.strb
    write_data_reg_last := io.master.writeData.bits.last
    write_data_reg := io.master.writeData.bits.data
    write_data_reg_valid := true.B
  }.otherwise {
    write_data_reg := write_data_reg
    write_data_reg_valid := false.B /* (Hsuan) */ // write_data_reg_valid
    write_data_reg_strb := write_data_reg_strb
    write_data_reg_last := write_data_reg_last
  }

  /* Hsuan */ io.master.writeData.ready := io.slave(write_port_reg).writeData.ready
  /* when(write_data_reg_valid) {
    io.master.writeData.ready := false.B
  }.otherwise {
    io.master.writeData.ready := true.B
  }*/
 
  when(io.slave(write_port_reg).writeResp.valid /* (Hsuan) */ /* && write_data_reg_valid === true.B */) {
    io.master.writeResp.bits := io.slave(write_port_reg).writeResp.bits
    io.master.writeResp.valid := true.B
    when(io.master.writeResp.ready) {
      io.slave(write_port_reg).writeResp.ready := true.B
      write_data_reg_valid := false.B
      write_addr_reg_valid := false.B
    }
  }.otherwise {
    io.master.writeResp.bits := 0.U
    io.slave(write_port_reg).writeResp.ready := false.B
    io.master.writeResp.valid := false.B
  }

  io.slave(write_port_reg).writeAddr.bits.addr := write_addr_reg
  io.slave(write_port_reg).writeAddr.bits.len := write_addr_reg_len
  io.slave(write_port_reg).writeAddr.bits.size := write_addr_reg_size
  io.slave(write_port_reg).writeAddr.bits.burst := write_addr_reg_burst
  io.slave(write_port_reg).writeAddr.valid := write_addr_reg_valid
  io.slave(write_port_reg).writeData.bits.data := write_data_reg
  io.slave(write_port_reg).writeData.bits.strb := write_data_reg_strb
  io.slave(write_port_reg).writeData.bits.last := write_data_reg_last
  io.slave(write_port_reg).writeData.valid := write_data_reg_valid
}
