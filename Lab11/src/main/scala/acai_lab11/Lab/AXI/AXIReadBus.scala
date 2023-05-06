package acai_lab11.AXI

import chisel3._
import chisel3.util._

class readMaster(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val readAddr = Flipped(Decoupled(new AXIAddress(addrWidth)))
  val readData = Decoupled(new AXIReadData(dataWidth))
}

class readSlave(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val readAddr = Decoupled(new AXIAddress(addrWidth))
  val readData = Flipped(Decoupled(new AXIReadData(dataWidth)))
}

class AXIReadBus(val mSlaves: Int, val addrWidth: Int, val dataWidth: Int, val addrMap: List[(UInt, UInt)]) extends Module {
  val io = IO(new Bundle {
    val master = new readMaster(addrWidth, dataWidth)
    val slave = Vec(mSlaves, new readSlave(addrWidth, dataWidth))
  })

  val read_port = WireDefault(0.U(1.W))
  val read_port_reg = RegInit(0.U(1.W))
  val read_addr_reg = RegInit(0.U((addrWidth).W))
  val read_addr_reg_len = RegInit(0.U(8.W))
  val read_addr_reg_size = RegInit(0.U(3.W))
  val read_addr_reg_burst = RegInit(0.U(2.W))
  val read_addr_reg_valid = RegInit(false.B)
  val slave_read_startAddr = Wire(Vec(mSlaves, UInt(addrWidth.W)))
  val slave_read_endAddr = Wire(Vec(mSlaves, UInt(dataWidth.W)))

  for (i <- 0 until addrMap.length) {
    slave_read_startAddr(i) := addrMap(i)._1
    slave_read_endAddr(i) := addrMap(i)._2
  }

  for (i <- 0 until mSlaves) {
    io.slave(i).readAddr.valid := false.B
    io.slave(i).readData.ready := false.B
    io.slave(i).readAddr.bits.addr := 0.U
    io.slave(i).readAddr.bits.len := 0.U
    io.slave(i).readAddr.bits.size := 0.U
    io.slave(i).readAddr.bits.burst := 0.U
    when(slave_read_startAddr(i) <= io.master.readAddr.bits.addr && io.master.readAddr.bits.addr < slave_read_endAddr(i)) {
      read_port := i.U // 找出slave的port
    }
  }

  io.master.readData.valid := false.B
  io.master.readAddr.ready := false.B
  io.master.readData.bits.data := 0.U
  io.master.readData.bits.last := false.B

  when(io.master.readAddr.valid && read_addr_reg_valid === false.B) {
    read_port_reg := read_port
    read_addr_reg := io.master.readAddr.bits.addr
    read_addr_reg_len := io.master.readAddr.bits.len
    read_addr_reg_size := io.master.readAddr.bits.size
    read_addr_reg_burst := io.master.readAddr.bits.burst
    read_addr_reg_valid := true.B
  }.otherwise {
    read_addr_reg := read_addr_reg
    read_addr_reg_valid := read_addr_reg_valid
    read_addr_reg_len := read_addr_reg_len
    read_addr_reg_size := read_addr_reg_size
    read_addr_reg_burst := read_addr_reg_burst
  }

  when(read_addr_reg_valid) {
    io.master.readAddr.ready := false.B
  }.otherwise {
    io.master.readAddr.ready := true.B
  }

  when(io.slave(read_port_reg).readData.valid) {
    io.master.readData.valid := true.B
    io.master.readData.bits.data := io.slave(read_port_reg).readData.bits.data
    io.master.readData.bits.resp := io.slave(read_port_reg).readData.bits.resp
    io.master.readData.bits.last := io.slave(read_port_reg).readData.bits.last
    when(io.master.readData.ready) {
      when(io.slave(read_port_reg).readData.bits.last){
        read_addr_reg_valid := false.B
      }
      io.slave(read_port_reg).readData.ready := true.B
    }.otherwise {
      io.master.readData.valid := false.B
      io.slave(read_port_reg).readData.ready := false.B
    }
  }.otherwise {
    io.master.readData.valid := false.B
    io.master.readData.bits.data := 0.U
    io.master.readData.bits.resp := 0.U
    io.master.readData.bits.last := false.B
    io.slave(read_port_reg).readData.ready := false.B
  }

  io.slave(read_port_reg).readAddr.bits.addr := read_addr_reg
  io.slave(read_port_reg).readAddr.bits.len := read_addr_reg_len
  io.slave(read_port_reg).readAddr.bits.size := read_addr_reg_size
  io.slave(read_port_reg).readAddr.bits.burst := read_addr_reg_burst
  io.slave(read_port_reg).readAddr.valid := read_addr_reg_valid
}
