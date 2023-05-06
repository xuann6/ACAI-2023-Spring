package lab10.PiplinedCPU.DatapathModule

import chisel3._
import chisel3.util._
import lab10.PiplinedCPU.DatapathModule.DatapathComponent._

class Path_EXE(addrWidth:Int) extends Module {
    val io = IO(new Bundle{
        // datapath
        val EXE_pc_in = Input(UInt(addrWidth.W))
        val EXE_imm_in = Input(UInt(32.W))
        val EXE_rs1_rdata_in = Input(UInt(32.W))
        val EXE_rs2_rdata_in = Input(UInt(32.W))
        // control signal
        val E_ASel = Input(UInt(2.W))
        val E_BSel = Input(Bool())
        val E_BrUn = Input(Bool())
        val E_ALUSel = Input(UInt(15.W))

        val EXE_target_pc_out = Output(UInt(addrWidth.W))
        val EXE_alu_out = Output(UInt(32.W))
        val EXE_rs2_rdata_out = Output(UInt(32.W))
        val E_BrEq = Output(Bool())
        val E_BrLT = Output(Bool())
        
        // Test
        val alu_src1 = Output(UInt(32.W))
        val alu_src2 = Output(UInt(32.W))
        val EXE_src1 = Output(UInt(32.W))
        val EXE_src2 = Output(UInt(32.W))

        /* lab 10-4 */
        val v_EXE_rs1_rdata_in = Input(UInt(512.W))
        val v_EXE_rs2_rdata_in = Input(UInt(512.W))
        val v_E_ASel = Input(Bool())
        val v_E_BSel = Input(UInt(2.W))
        val v_E_ALUSel = Input(UInt(4.W))
        val v_EXE_alu_out = Output(UInt(512.W))

        /* lab 10-4 (Hsuan) */
        val DM_v_wdata = Output(UInt(512.W))
    })
    val alu_src1 = Wire(UInt(32.W))
    val alu_src2 = Wire(UInt(32.W))

    //Branch Comparator
    val bc = Module(new BranchComp)
    bc.io.BrUn := io.E_BrUn
    bc.io.src1 := io.EXE_rs1_rdata_in
    bc.io.src2 := io.EXE_rs2_rdata_in
    io.E_BrEq := bc.io.BrEq
    io.E_BrLT := bc.io.BrLT

    //ALU
    val alu = Module(new ALU)
    alu_src1 := MuxLookup(io.E_ASel,io.EXE_rs1_rdata_in,Seq(
        0.U -> io.EXE_rs1_rdata_in,
        1.U -> io.EXE_pc_in,
        2.U -> 0.U(32.W)
    ))
    alu_src2 := MuxLookup(io.E_BSel,io.EXE_rs2_rdata_in,Seq(
        0.U -> io.EXE_rs2_rdata_in,
        1.U -> io.EXE_imm_in,
    ))
    alu.io.src1 := alu_src1
    alu.io.src2 := alu_src2
    alu.io.ALUSel := io.E_ALUSel
    io.EXE_alu_out := alu.io.out

    // other IO
    io.EXE_rs2_rdata_out := io.EXE_rs2_rdata_in
    io.EXE_target_pc_out := alu.io.out(addrWidth-1,0) // pc target from alu out
    io.alu_src1 := alu_src1
    io.alu_src2 := alu_src2
    io.EXE_src1 := io.EXE_rs1_rdata_in
    io.EXE_src2 := io.EXE_rs2_rdata_in

    /* lab 10-4 */
    val v_alu = Module(new Vector_ALU)
    v_alu.io.vector_src1    :=  MuxLookup(io.v_E_ASel,io.v_EXE_rs1_rdata_in,Seq(
        // To Be Mofified
        false.B  -> io.v_EXE_rs1_rdata_in,
        true.B -> 0.U(512.W),
    ))
    v_alu.io.vector_src2    :=  MuxLookup(io.v_E_BSel,io.v_EXE_rs2_rdata_in,Seq(
        // To Be Mofified
        0.U  -> io.v_EXE_rs2_rdata_in,
        1.U  -> Cat(0.U(480.W),io.EXE_rs2_rdata_in), // for vx-operation (vector-scalar) 
        2.U  -> 0.U(512.W),
    ))
    v_alu.io.vector_ALUSel  := io.v_E_ALUSel
    io.v_EXE_alu_out := v_alu.io.vector_out

    /* lab 10-4 (Hsuan) */
    io.DM_v_wdata := io.v_EXE_rs2_rdata_in
}
