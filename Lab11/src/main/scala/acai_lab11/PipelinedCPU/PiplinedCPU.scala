package acai_lab11.PiplinedCPU

import chisel3._
import chisel3.util._

import acai_lab11.Memory._
import acai_lab11.IF._
import acai_lab11.PiplinedCPU.StageRegister._
import acai_lab11.PiplinedCPU.Controller._        // use acai_lab11_2 controller
import acai_lab11.PiplinedCPU.DatapathModule._
import acai_lab11.PiplinedCPU.DatapathModule.DatapathComponent._
import acai_lab11.PiplinedCPU.opcode_map._
import acai_lab11.PiplinedCPU.wide._


class PiplinedCPU(memAddrWidth: Int, memDataWidth: Int) extends Module {
    val io = IO(new Bundle{
        //InstMem
        val InstMem = new MemIF_CPU(memAddrWidth, memDataWidth) 
        
        //DataMem
        val DataMem = new MemIF_CPU(memAddrWidth, memDataWidth) 

        //System
        val regs = Output(Vec(32,UInt(32.W)))
        val vector_regs = Output(Vec(32,UInt(512.W)))
        val Hcf = Output(Bool())

        // Test
        val E_Branch_taken = Output(Bool())
        val Flush = Output(Bool())
        val Stall_MA = Output(Bool())
        val Stall_DH = Output(Bool())
        val IF_PC = Output(UInt(memAddrWidth.W))
        val ID_PC = Output(UInt(memAddrWidth.W))
        val EXE_PC = Output(UInt(memAddrWidth.W))
        val MEM_PC = Output(UInt(memAddrWidth.W))
        val WB_PC = Output(UInt(memAddrWidth.W))
        val EXE_src1 = Output(UInt(32.W))
        val EXE_src2 = Output(UInt(32.W))
        val ALU_src1 = Output(UInt(32.W))
        val ALU_src2 = Output(UInt(32.W))
        val EXE_alu_out = Output(UInt(32.W))
        val WB_rd = Output(UInt(5.W))
        val WB_wdata = Output(UInt(32.W))
        val EXE_Jump = Output(Bool())
        val EXE_Branch = Output(Bool())
        val MEM_LOAD = Output(Bool())
        val MEM_STORE = Output(Bool())
        val MEM_L_Bytes = Output(UInt(7.W))
        val MEM_W_Bytes = Output(UInt(7.W))

        // (Hsuan) lab 10-3 test
        val Mem_Read = Output(Bool())
        val Mem_Write = Output(Bool())
        val Mem_Read_Write_Length = Output(UInt(4.W))


    })
    /*****  Pipeline Stages Registers Module for holding data *****/
    // stage Registers
    val stage_IF = Module(new Reg_IF(memAddrWidth))
    val stage_ID = Module(new Reg_ID(memAddrWidth))
    val stage_EXE = Module(new Reg_EXE(memAddrWidth))
    val stage_MEM = Module(new Reg_MEM(memAddrWidth))
    val stage_WB = Module(new Reg_WB(memAddrWidth))

    // 5 pipe stage datapath modules
    val datapath_IF = Module(new Path_IF(memAddrWidth))
    val datapath_ID = Module(new Path_ID(memAddrWidth))
    val datapath_EXE = Module(new Path_EXE(memAddrWidth))
    val datapath_MEM = Module(new Path_MEM(memAddrWidth))
    val datapath_WB = Module(new Path_WB(memAddrWidth))

    // 1 contorller module
    val contorller = Module(new Controller(memAddrWidth))

    /* Wire Connect */
    // === IF stage reg (PC reg) ======================================================
    stage_IF.io.Stall := (contorller.io.Hcf||contorller.io.Stall_WB_ID_DH||contorller.io.Stall_EXE_ID_DH||contorller.io.Stall_MEM_ID_DH||contorller.io.Stall_MA) // To Be Modified
    stage_IF.io.next_pc_in := datapath_IF.io.next_pc

    // IF Block Datapath
    datapath_IF.io.PCSel := contorller.io.PCSel
    datapath_IF.io.IF_pc_in := stage_IF.io.pc
    datapath_IF.io.EXE_pc_in := stage_EXE.io.pc
    datapath_IF.io.EXE_target_pc_in := datapath_EXE.io.EXE_target_pc_out 
    datapath_IF.io.Mem_data := io.InstMem.rdata(31,0)

    // --- Insruction Memory Interface
    io.InstMem.Mem_R := contorller.io.IM_Mem_R
    io.InstMem.Mem_W :=  contorller.io.IM_Mem_W
    io.InstMem.Length :=  contorller.io.IM_Length
    io.InstMem.Wide :=  contorller.io.DM_wide
    io.InstMem.raddr := datapath_IF.io.Mem_Addr
    io.InstMem.waddr := 0.U // not used
    io.InstMem.wdata := 0.U // not used

    // === ID stage reg ==============================================================
    stage_ID.io.Flush := contorller.io.Flush_BH    // To Be Modified
    stage_ID.io.Stall := (contorller.io.Hcf || contorller.io.Stall_WB_ID_DH||contorller.io.Stall_EXE_ID_DH||contorller.io.Stall_MEM_ID_DH||contorller.io.Stall_MA) & ~(contorller.io.Flush_BH)     // To Be Modified
    stage_ID.io.inst_in := datapath_IF.io.inst
    stage_ID.io.pc_in := stage_IF.io.pc

    // ID Block Datapath
    datapath_ID.io.ID_inst_in := stage_ID.io.inst
    datapath_ID.io.WB_index := stage_WB.io.inst(11,7)
    datapath_ID.io.WB_wdata := datapath_WB.io.WB_wdata
    datapath_ID.io.WB_RegWEn := contorller.io.W_RegWEn
    datapath_ID.io.ImmSel := contorller.io.D_ImmSel
    datapath_ID.io.v_WB_wdata := datapath_WB.io.v_WB_wdata
    datapath_ID.io.v_WB_RegWEn := contorller.io.v_W_RegWEn
    datapath_ID.io.v_WB_index := stage_WB.io.inst(11,7)

    // === EXE stage reg ==============================================================
    stage_EXE.io.Flush := (contorller.io.Flush_BH || contorller.io.Flush_WB_ID_DH) & ~(contorller.io.Hcf ||contorller.io.Stall_MA) // To Be Modified
    stage_EXE.io.Stall := contorller.io.Hcf ||contorller.io.Stall_MA  // To Be Modified
    stage_EXE.io.pc_in := stage_ID.io.pc
    stage_EXE.io.inst_in := stage_ID.io.inst
    stage_EXE.io.imm_in := datapath_ID.io.imm
    stage_EXE.io.rs1_rdata_in := datapath_ID.io.ID_rs1_rdata
    stage_EXE.io.rs2_rdata_in := datapath_ID.io.ID_rs2_rdata
    stage_EXE.io.v_rs1_rdata_in := datapath_ID.io.v_ID_rs1_rdata
    stage_EXE.io.v_rs2_rdata_in := datapath_ID.io.v_ID_rs2_rdata
    stage_EXE.io.v_rd_rdata_in := datapath_ID.io.v_ID_rd_rdata

    // EXE Block Datapath
    datapath_EXE.io.EXE_pc_in := stage_EXE.io.pc
    datapath_EXE.io.EXE_imm_in := stage_EXE.io.imm
    datapath_EXE.io.EXE_rs1_rdata_in := stage_EXE.io.rs1_rdata
    datapath_EXE.io.EXE_rs2_rdata_in := stage_EXE.io.rs2_rdata
    datapath_EXE.io.E_ASel := contorller.io.E_ASel
    datapath_EXE.io.E_BSel := contorller.io.E_BSel
    datapath_EXE.io.E_BrUn := contorller.io.E_BrUn
    datapath_EXE.io.E_ALUSel := contorller.io.E_ALUSel
    datapath_EXE.io.v_E_ALUSel := contorller.io.v_E_ALUSel
    datapath_EXE.io.v_E_ASel := contorller.io.v_E_ASel
    datapath_EXE.io.v_E_BSel := contorller.io.v_E_BSel
    datapath_EXE.io.v_EXE_rs1_rdata_in := stage_EXE.io.v_rs1_rdata
    datapath_EXE.io.v_EXE_rs2_rdata_in := stage_EXE.io.v_rs2_rdata
    datapath_EXE.io.v_EXE_rd_rdata_in := stage_EXE.io.v_rd_rdata
    
    // === MEM stage reg ==============================================================
    stage_MEM.io.Stall := contorller.io.Hcf ||contorller.io.Stall_MA     // To Be Modified
    stage_MEM.io.pc_in := stage_EXE.io.pc
    stage_MEM.io.inst_in := stage_EXE.io.inst
    stage_MEM.io.DM_wdata_in := datapath_EXE.io.EXE_rs2_rdata_out
    stage_MEM.io.alu_out_in := datapath_EXE.io.EXE_alu_out
    stage_MEM.io.v_alu_out_in := datapath_EXE.io.v_EXE_alu_out
    stage_MEM.io.v_DM_wdata_in := datapath_EXE.io.v_EXE_rd_rdata_out

    // MEM Block Datapath
    datapath_MEM.io.MEM_pc_in := stage_MEM.io.pc
    datapath_MEM.io.MEM_alu_out_in := stage_MEM.io.alu_out
    datapath_MEM.io.MEM_DM_wdata_in := stage_MEM.io.DM_wdata
    datapath_MEM.io.Mem_Data := io.DataMem.rdata(31,0)
    datapath_MEM.io.v_Mem_Data := io.DataMem.rdata
    datapath_MEM.io.v_MEM_alu_out_in := stage_MEM.io.v_alu_out
    datapath_MEM.io.v_MEM_DM_wdata_in := stage_MEM.io.v_DM_wdata

    // --- Data Memory Interface
    io.DataMem.Mem_R := contorller.io.DM_Mem_R
    io.DataMem.Mem_W :=  contorller.io.DM_Mem_W
    io.DataMem.Length :=  contorller.io.DM_Length
    io.DataMem.Wide := contorller.io.DM_wide
    io.DataMem.raddr := datapath_MEM.io.Mem_Addr
    io.DataMem.waddr := datapath_MEM.io.Mem_Addr
    io.DataMem.wdata := Mux(stage_MEM.io.inst(6,0) === OPVS,datapath_MEM.io.v_Mem_Write_Data,datapath_MEM.io.Mem_Write_Data)


    // === WB stage reg ==============================================================
    stage_WB.io.Stall := contorller.io.Hcf ||contorller.io.Stall_MA    // To Be Modified
    stage_WB.io.pc_plus4_in := datapath_MEM.io.MEM_pc_plus_4
    stage_WB.io.inst_in := stage_MEM.io.inst
    stage_WB.io.alu_out_in := datapath_MEM.io.MEM_alu_out
    stage_WB.io.ld_data_in := datapath_MEM.io.MEM_ld_data
    stage_WB.io.v_alu_out_in := datapath_MEM.io.v_MEM_alu_out
    stage_WB.io.v_ld_data_in := datapath_MEM.io.v_MEM_ld_data

    // WB Block Datapath
    datapath_WB.io.WB_pc_plus4_in := stage_WB.io.pc_plus4
    datapath_WB.io.WB_alu_out_in := stage_WB.io.alu_out
    datapath_WB.io.WB_ld_data_in := stage_WB.io.ld_data
    datapath_WB.io.W_WBSel := contorller.io.W_WBSel
    datapath_WB.io.v_WB_alu_out_in := stage_WB.io.v_alu_out
    datapath_WB.io.v_WB_ld_data_in := stage_WB.io.v_ld_data_in
    datapath_WB.io.v_W_WBSel := contorller.io.v_W_WBSel

    /* Controller */
    contorller.io.IF_Inst := io.InstMem.rdata
    contorller.io.ID_Inst := stage_ID.io.inst
    contorller.io.EXE_Inst := stage_EXE.io.inst
    contorller.io.MEM_Inst := stage_MEM.io.inst
    contorller.io.WB_Inst := stage_WB.io.inst

    contorller.io.E_BrEq := datapath_EXE.io.E_BrEq
    contorller.io.E_BrLT := datapath_EXE.io.E_BrLT

    contorller.io.ID_pc := stage_ID.io.pc
    
    contorller.io.EXE_target_pc := datapath_EXE.io.EXE_target_pc_out

    contorller.io.IM_Valid := io.InstMem.Valid
    contorller.io.DM_Valid := io.DataMem.Valid


    /* System */
    io.regs := datapath_ID.io.regs
    io.vector_regs := datapath_ID.io.vector_regs
    io.Hcf := contorller.io.Hcf

    /* Test */
    io.E_Branch_taken := contorller.io.E_Branch_taken
    io.Flush := contorller.io.Flush_BH || contorller.io.Flush_WB_ID_DH
    io.Stall_DH := contorller.io.Stall_WB_ID_DH ||contorller.io.Stall_EXE_ID_DH||contorller.io.Stall_MEM_ID_DH
    io.Stall_MA := contorller.io.Stall_MA 
    io.IF_PC := stage_IF.io.pc
    io.ID_PC := stage_ID.io.pc
    io.EXE_PC := stage_EXE.io.pc
    io.MEM_PC := stage_MEM.io.pc
    io.WB_PC := Mux(stage_WB.io.pc_plus4 > 0.U ,stage_WB.io.pc_plus4 - 4.U,stage_WB.io.pc_plus4)
    io.EXE_alu_out := datapath_EXE.io.EXE_alu_out
    io.EXE_src1 := datapath_EXE.io.EXE_src1
    io.EXE_src2 := datapath_EXE.io.EXE_src2
    io.ALU_src1 := datapath_EXE.io.alu_src1
    io.ALU_src2 := datapath_EXE.io.alu_src2
    io.WB_wdata := datapath_WB.io.WB_wdata
    io.WB_rd := stage_WB.io.inst(11,7)
    io.EXE_Jump := (stage_EXE.io.inst(6, 0)===JAL) || (stage_EXE.io.inst(6, 0)===JALR)
    io.EXE_Branch := (stage_EXE.io.inst(6, 0)===BRANCH)
    io.MEM_LOAD := (stage_MEM.io.inst(6, 0)===LOAD || stage_MEM.io.inst(6, 0)===OPVL)
    io.MEM_STORE := (stage_MEM.io.inst(6, 0)===STORE || stage_MEM.io.inst(6, 0)===OPVS)
    io.MEM_L_Bytes := Mux(contorller.io.DM_wide === "b1000".U,64.U,4.U) 
    io.MEM_W_Bytes := Mux(contorller.io.DM_wide === "b1000".U,64.U,4.U)

    // (Hsuan) lab 10-3 test
    io.Mem_Read := contorller.io.DM_Mem_R
    io.Mem_Write := contorller.io.DM_Mem_W
    io.Mem_Read_Write_Length := contorller.io.DM_Length

}
