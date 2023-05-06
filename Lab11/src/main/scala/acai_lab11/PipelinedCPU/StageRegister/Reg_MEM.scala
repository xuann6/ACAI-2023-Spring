package acai_lab11.PiplinedCPU.StageRegister

import chisel3._
import chisel3.util._

class Reg_MEM(addrWidth:Int) extends Module {
    val io = IO(new Bundle{
        val Stall = Input(Bool())
        
        val pc_in = Input(UInt(addrWidth.W))
        val inst_in = Input(UInt(32.W))
        val alu_out_in = Input(UInt(32.W))
        val DM_wdata_in = Input(UInt(32.W))

        val pc = Output(UInt(addrWidth.W))
        val inst = Output(UInt(32.W))
        val alu_out = Output(UInt(32.W))
        val DM_wdata = Output(UInt(32.W))
        
        /* acai_lab11-4 */
        val v_alu_out_in = Input(UInt(512.W))
        val v_alu_out = Output(UInt(512.W))
        val v_DM_wdata_in = Input(UInt(512.W))
        val v_DM_wdata = Output(UInt(512.W))
    })
    
    // stage Registers
    val InstReg = RegInit(0.U(32.W))
    val pcReg =  RegInit(0.U(addrWidth.W))
    val aluReg = RegInit(0.U(32.W))
    val v_aluReg = RegInit(0.U(512.W))
    val wdataReg = RegInit(0.U(32.W))
    val v_wdataReg = RegInit(0.U(512.W))

    /*** stage Registers Action ***/
    when(io.Stall){
        InstReg := InstReg
        pcReg := pcReg
        aluReg := aluReg
        v_aluReg := v_aluReg
        wdataReg := wdataReg
        v_wdataReg := v_wdataReg
    }.otherwise{
        InstReg := io.inst_in
        pcReg := io.pc_in
        aluReg := io.alu_out_in
        v_aluReg := io.v_alu_out_in
        wdataReg := io.DM_wdata_in
        v_wdataReg := io.v_DM_wdata_in
    }
 
    io.inst := InstReg
    io.pc := pcReg
    io.alu_out := aluReg
    io.v_alu_out := v_aluReg
    io.DM_wdata := wdataReg
    io.v_DM_wdata := v_wdataReg
    
}
