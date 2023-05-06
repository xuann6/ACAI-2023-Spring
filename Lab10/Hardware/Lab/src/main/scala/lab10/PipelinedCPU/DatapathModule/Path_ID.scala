package lab10.PiplinedCPU.DatapathModule

import chisel3._
import chisel3.util._
import lab10.PiplinedCPU.DatapathModule.DatapathComponent._
<<<<<<< HEAD
import lab10.PiplinedCPU.opcode_map._
=======
>>>>>>> 301bdb3 (Finished Prepare Lab10-2)

class Path_ID(addrWidth:Int) extends Module {
    val io = IO(new Bundle{

        val ID_inst_in = Input(UInt(32.W))
        val WB_index = Input(UInt(5.W))
        val WB_wdata = Input(UInt(32.W))
        val WB_RegWEn = Input(Bool())
        val ImmSel = Input(UInt(3.W))

        val ID_rs1_rdata = Output(UInt(32.W))
        val ID_rs2_rdata = Output(UInt(32.W))
        val imm = Output(UInt(32.W))

        val regs = Output(Vec(32,UInt(32.W)))  // for monitor
        
        /* lab 10-4 */
        val vector_regs = Output(Vec(32,UInt(512.W)))  // for monitor
        val v_WB_index = Input(UInt(5.W))
        val v_WB_wdata = Input(UInt(512.W))
        val v_WB_RegWEn = Input(Bool())
        val v_ID_rs1_rdata = Output(UInt(512.W))
        val v_ID_rs2_rdata = Output(UInt(512.W))
    })
    // Inst Decode
    val rs1_index = io.ID_inst_in(19,15)
    val rs2_index = io.ID_inst_in(24,20)

    // Reg File Module
    val rf = Module(new RegFile(2))
    rf.io.wen := io.WB_RegWEn
    rf.io.waddr := io.WB_index
    rf.io.wdata := io.WB_wdata
    rf.io.raddr(0) := rs1_index
    rf.io.raddr(1) := rs2_index
    io.ID_rs1_rdata := rf.io.rdata(0)
    io.ID_rs2_rdata := rf.io.rdata(1)
    io.regs := rf.io.regs // for monitor

    // Imm Gen Module
    val ig = Module(new ImmGen)
    ig.io.ImmSel := io.ImmSel
    ig.io.inst_31_7 := io.ID_inst_in(31,7)
    io.imm := ig.io.imm

    // Vector Reg File Module
    val v_rf = Module(new Vector_RegFile(2))

    v_rf.io.vector_wen   := io.v_WB_RegWEn
    v_rf.io.vector_waddr := io.v_WB_index
    v_rf.io.vector_wdata := io.v_WB_wdata
    v_rf.io.vector_raddr(0) := rs1_index
    /* (Hsuan) */ v_rf.io.vector_raddr(1) := Mux(io.ID_inst_in(6,0)===OPVS, io.ID_inst_in(11,7), rs2_index)
    io.v_ID_rs1_rdata:= v_rf.io.vector_rdata(0)
    io.v_ID_rs2_rdata:= v_rf.io.vector_rdata(1)
    io.vector_regs := v_rf.io.vector_regs
}
