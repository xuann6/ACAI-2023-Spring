package acai_lab11.PiplinedCPU.Controller

import chisel3._
import chisel3.util._

import acai_lab11.PiplinedCPU.opcode_map._
import acai_lab11.PiplinedCPU.condition._
import acai_lab11.PiplinedCPU.inst_type._
import acai_lab11.PiplinedCPU.alu_op_map._
import acai_lab11.PiplinedCPU.pc_sel_map._
import acai_lab11.PiplinedCPU.wb_sel_map._
import acai_lab11.PiplinedCPU.forwarding_sel_map._
import acai_lab11.PiplinedCPU.vector_ALU_op._
import acai_lab11.PiplinedCPU.wide._

class Controller(memAddrWidth: Int) extends Module {
  val io = IO(new Bundle {
    // Memory control signal interface
    val IM_Mem_R = Output(Bool()) 
    val IM_Mem_W = Output(Bool()) 
    val IM_Length = Output(UInt(4.W))
    val IM_Valid = Input(Bool()) 

    val DM_Mem_R = Output(Bool()) 
    val DM_Mem_W = Output(Bool()) 
    val DM_Length = Output(UInt(5.W))
    val DM_wide = Output(UInt(4.W))
    val DM_Valid = Input(Bool()) 

    // branch Comp.
    val E_BrEq = Input(Bool())
    val E_BrLT = Input(Bool())

    // Branch Prediction
    val E_En = Output(Bool())
    val E_Branch_taken = Output(Bool())

    val ID_pc = Input(UInt(memAddrWidth.W))
    val EXE_target_pc = Input(UInt(memAddrWidth.W))

    // Flush
    val Flush_WB_ID_DH  = Output(Bool())

    val Flush_BH  = Output(Bool()) // branch hazard flush

    // Stall
    // To Be Modified
    val Stall_WB_ID_DH = Output(Bool()) //TBD
    val Stall_MEM_ID_DH = Output(Bool())
    val Stall_EXE_ID_DH = Output(Bool())

    
    val Stall_MA = Output(Bool()) //TBD

    // inst
    val IF_Inst = Input(UInt(32.W))
    val ID_Inst = Input(UInt(32.W))
    val EXE_Inst = Input(UInt(32.W))
    val MEM_Inst = Input(UInt(32.W))
    val WB_Inst = Input(UInt(32.W))

    // sel
    val PCSel = Output(UInt(2.W))
    val D_ImmSel = Output(UInt(3.W))
    val W_RegWEn = Output(Bool())
    val E_BrUn = Output(Bool())
    val E_ASel = Output(UInt(2.W))
    val E_BSel = Output(UInt(1.W))
    val E_ALUSel = Output(UInt(15.W))
    val W_WBSel = Output(UInt(2.W))

    val v_W_RegWEn = Output(Bool())
    val v_E_ASel = Output(UInt(1.W))
    val v_E_BSel = Output(UInt(1.W))
    val v_E_ALUSel = Output(UInt(2.W))
    val v_W_WBSel = Output(UInt(1.W))

    //val last = Inupt(Bool())

    val Hcf = Output(Bool())
  })
  // Inst Decode for each stage 
  val IF_opcode = io.IF_Inst(6, 0)

  val ID_opcode = io.ID_Inst(6, 0)
  val ID_funct3 = io.ID_Inst(14, 12)
  val ID_rs1 = io.ID_Inst(19,15)
  val ID_rs2 = io.ID_Inst(24,20)
  val ID_vs1 = io.ID_Inst(19,15)
  val ID_vs2 = io.ID_Inst(24,20)

  val EXE_opcode = io.EXE_Inst(6, 0)
  val EXE_funct3 = io.EXE_Inst(14, 12)
  val EXE_funct7 = io.EXE_Inst(31, 25)
  val EXE_rd = io.EXE_Inst(11,7)
  val EXE_vd = io.EXE_Inst(11,7)

  val MEM_opcode = io.MEM_Inst(6, 0)
  val MEM_funct3 = io.MEM_Inst(14, 12)
  val MEM_rd = io.MEM_Inst(11,7)
  val MEM_vd = io.MEM_Inst(11,7)


  val WB_opcode = io.WB_Inst(6, 0)
  val WB_rd = io.WB_Inst(11, 7)
  val WB_vd = io.WB_Inst(11, 7)


  // Control signal - Branch/Jump
  val E_En = Wire(Bool())
  E_En := (EXE_opcode===BRANCH || EXE_opcode===JAL || EXE_opcode===JALR)         // To Be Modified
  val E_Branch_taken = Wire(Bool())
  E_Branch_taken := MuxLookup(EXE_opcode, false.B, Seq(
          BRANCH -> MuxLookup(EXE_funct3, false.B, Seq(
            "b000".U(3.W) -> io.E_BrEq,
            "b001".U(3.W) -> (!io.E_BrEq),
            "b100".U(3.W) -> io.E_BrLT,
            "b101".U(3.W) -> (!io.E_BrLT),
            "b110".U(3.W) -> io.E_BrLT,
            "b111".U(3.W) -> (!io.E_BrLT)
          )),
          JALR -> true.B,
          JAL -> true.B,
        ))    // To Be Modified
        
  io.E_En := E_En
  io.E_Branch_taken := E_Branch_taken

  // pc predict miss signal
  val Predict_Miss = Wire(Bool())
  Predict_Miss := (E_En && E_Branch_taken && io.ID_pc=/=io.EXE_target_pc)

  // Control signal - PC
  when(Predict_Miss){
    io.PCSel := EXE_T_PC
  }.otherwise{
    io.PCSel := IF_PC_PLUS_4
  }
  // Control signal - Branch comparator
  io.E_BrUn := (io.EXE_Inst(13) === 1.U)

  // Control signal - Immediate generator
  io.D_ImmSel := MuxLookup(ID_opcode, 0.U, Seq(
    OP -> R_type,
    OP_IMM -> I_type,
    LOAD -> I_type,
    JALR -> I_type,
    STORE -> S_type,
    BRANCH -> B_type,
    AUIPC -> U_type,
    LUI -> U_type,
    JAL -> J_type,
  )) // To Be Modified

  // Control signal - Scalar ALU
  io.E_ASel := MuxLookup(EXE_opcode, 0.U, Seq(
    OP -> 0.U,
    OP_IMM -> 0.U,
    LOAD -> 0.U,
    STORE -> 0.U,
    JALR -> 0.U,
    JAL -> 1.U,
    BRANCH -> 1.U,
    LUI -> 2.U,
    AUIPC -> 1.U

  ))    // To Be Modified
  io.E_BSel := Mux(EXE_opcode === OP , 0.U , 1.U) // To Be Modified
  
  io.E_ALUSel := MuxLookup(EXE_opcode, (Cat(0.U(7.W), "b11111".U, 0.U(3.W))), Seq(
    OP -> (Cat(EXE_funct7, "b11111".U, EXE_funct3)),
    OP_IMM -> Mux(EXE_funct3 === "b101".U,(Cat(EXE_funct7, "b11111".U, EXE_funct3))  ,(Cat(0.U(7.W), "b11111".U, EXE_funct3))),
    LOAD -> "b0000000_11111_000".U,
    STORE -> "b0000000_11111_000".U,
    JALR -> "b0000000_11111_000".U,
    JAL -> "b0000000_11111_000".U,
    BRANCH -> "b0000000_11111_000".U,
    LUI -> "b0000000_11111_000".U,
    AUIPC -> "b0000000_11111_000".U
  )) // To Be Modified

  // Control signal - Data Memory
  io.DM_Mem_R := (MEM_opcode===LOAD || MEM_opcode===OPVL)
  io.DM_Mem_W := (MEM_opcode===STORE || MEM_opcode===OPVS)
  io.DM_Length := MuxLookup(MEM_opcode,1.U,Seq(
    LOAD -> 1.U,
    STORE -> 1.U,
    OPVL -> 16.U,
    OPVS -> 16.U
  )) // length
  io.DM_wide := Mux(MEM_opcode===STORE || MEM_opcode===LOAD,  Cat(0.U(1.W),MEM_funct3),Vec_8Bytes)

  // Control signal - Inst Memory
  io.IM_Mem_R := true.B // always true
  io.IM_Mem_W := false.B // always false
  io.IM_Length := "b0010".U // always load a word(inst)

  // Control signal - Scalar Write Back
  io.W_RegWEn := MuxLookup(WB_opcode, false.B, Seq(
    OP -> true.B,
    OP_IMM -> true.B,
    LOAD -> true.B,
    LUI -> true.B,
    STORE -> false.B,
    JALR -> true.B,
    JAL -> true.B,
    BRANCH -> false.B,
    AUIPC -> true.B
  ))  // To Be Modified
  
  
  io.W_WBSel := MuxLookup(WB_opcode, ALUOUT, Seq(
    LOAD -> LD_DATA,
    OP -> ALUOUT,
    OP_IMM -> ALUOUT,
    LUI -> ALUOUT,
    JALR -> PC_PLUS_4,
    JAL -> PC_PLUS_4,
    AUIPC -> ALUOUT
  )) // To Be Modified

  // Control signal - Vector Write Back
  io.v_W_RegWEn := MuxLookup(WB_opcode, false.B, Seq(
    OPIV -> true.B,
    OPVL -> true.B,
    OPVS -> false.B
  ))
  io.v_E_ALUSel := MuxLookup(EXE_opcode,0.U,Seq(
    OPIV -> (Mux(EXE_funct3 === 0.U,0.U,1.U)),   // vadd_vv or vmul_vx
    OPVL -> 2.U,
    OPVS -> 2.U
  )) 
  io.v_E_ASel := Mux(EXE_opcode === OPIV && EXE_funct3 === VADD_VV , 0.U, 1.U)
  io.v_E_BSel := Mux(EXE_opcode === OPIV , 0.U, 1.U)

  io.v_W_WBSel := Mux(WB_opcode === OPVL , 1.U, 0.U)
  // Control signal - Others
  io.Hcf := (IF_opcode === HCF)

  /****************** Data Hazard ******************/
  // Use rs in ID stage 
  val is_D_use_rs1 = Wire(Bool()) 
  val is_D_use_rs2 = Wire(Bool())

  is_D_use_rs1 := MuxLookup(ID_opcode,false.B,Seq(
    BRANCH -> true.B,
    OP -> true.B,
    OP_IMM -> true.B,
    LOAD -> true.B,
    LUI -> false.B,
    STORE -> true.B,
    JALR -> true.B,
    JAL -> false.B,
    AUIPC -> false.B,
    OPVL -> true.B,
    OPVS -> true.B,
    OPIV -> Mux(ID_funct3 =/= 0.U,true.B,false.B)
  ))   // To Be Modified
  is_D_use_rs2 := MuxLookup(ID_opcode,false.B,Seq(
    BRANCH -> true.B,
    OP -> true.B,
    OP_IMM -> false.B,
    LOAD -> false.B,
    LUI -> false.B,
    STORE -> true.B,
    JALR -> false.B,
    JAL -> false.B,
    AUIPC -> false.B,

  ))   // To Be Modified

  // Use rd in WB stage
  val is_W_use_rd = Wire(Bool())
  val is_M_use_rd = Wire(Bool())
  val is_E_use_rd = Wire(Bool())
  is_W_use_rd := MuxLookup(WB_opcode,false.B,Seq(
    OP_IMM -> true.B,
    BRANCH -> false.B,
    OP -> true.B,
    LOAD -> true.B,
    LUI -> true.B,
    STORE -> false.B,
    JALR -> true.B,
    JAL -> true.B,
    AUIPC -> true.B,
    OPIV -> true.B,
    OPVL -> true.B
  ))   // To Be Modified

  is_E_use_rd := MuxLookup(EXE_opcode,false.B,Seq(
    OP_IMM -> true.B,
    BRANCH -> false.B,
    OP -> true.B,
    LOAD -> true.B,
    LUI -> true.B,
    STORE -> false.B,
    JALR -> true.B,
    JAL -> true.B,
    AUIPC -> true.B,
    OPIV -> true.B,
    OPVL -> true.B
  ))
  is_M_use_rd := MuxLookup(MEM_opcode,false.B,Seq(
    OP_IMM -> true.B,
    BRANCH -> false.B,
    OP -> true.B,
    LOAD -> true.B,
    LUI -> true.B,
    STORE -> false.B,
    JALR -> true.B,
    JAL -> true.B,
    AUIPC -> true.B,
    OPIV -> true.B,
    OPVL -> true.B
  ))
  val is_D_use_vs1 = Wire(Bool())
  val is_D_use_vs2 = Wire(Bool())

  is_D_use_vs1 := Mux(ID_opcode === OPIV && ID_funct3 === VADD_VV ,true.B,false.B) 
  is_D_use_vs2 := Mux(ID_opcode === OPIV,true.B,false.B) 


  // Hazard condition (rd, rs overlap)
  val is_D_rs1_W_rd_overlap = Wire(Bool())
  val is_D_rs2_W_rd_overlap = Wire(Bool())
  val is_D_rs1_E_rd_overlap = Wire(Bool())
  val is_D_rs2_E_rd_overlap = Wire(Bool())
  val is_D_rs1_M_rd_overlap = Wire(Bool())
  val is_D_rs2_M_rd_overlap = Wire(Bool())

  val is_D_vs1_W_vd_overlap = Wire(Bool())
  val is_D_vs1_E_vd_overlap = Wire(Bool())
  val is_D_vs1_M_vd_overlap = Wire(Bool())
  val is_D_vs2_W_vd_overlap = Wire(Bool())
  val is_D_vs2_E_vd_overlap = Wire(Bool())
  val is_D_vs2_M_vd_overlap = Wire(Bool())



  is_D_rs1_W_rd_overlap := is_D_use_rs1 && is_W_use_rd && (ID_rs1 === WB_rd) && (WB_rd =/= 0.U(5.W)) 
  is_D_rs2_W_rd_overlap := is_D_use_rs2 && is_W_use_rd && (ID_rs2 === WB_rd) && (WB_rd =/= 0.U(5.W)) 
  is_D_rs1_E_rd_overlap := is_D_use_rs1 && is_E_use_rd && (ID_rs1 === EXE_rd) && (EXE_rd =/= 0.U(5.W)) 
  is_D_rs2_E_rd_overlap := is_D_use_rs2 && is_E_use_rd && (ID_rs2 === EXE_rd) && (EXE_rd =/= 0.U(5.W)) 
  is_D_rs1_M_rd_overlap := is_D_use_rs1 && is_M_use_rd && (ID_rs1 === MEM_rd) && (MEM_rd =/= 0.U(5.W)) 
  is_D_rs2_M_rd_overlap := is_D_use_rs2 && is_M_use_rd && (ID_rs2 === MEM_rd) && (MEM_rd =/= 0.U(5.W)) 

  is_D_vs1_W_vd_overlap := is_D_use_vs1 && is_W_use_rd && (ID_vs1 === WB_vd) && (WB_vd =/= 0.U(5.W)) 
  is_D_vs1_E_vd_overlap := is_D_use_vs1 && is_E_use_rd && (ID_vs1 === EXE_vd) && (EXE_vd =/= 0.U(5.W)) 
  is_D_vs1_M_vd_overlap := is_D_use_vs1 && is_M_use_rd && (ID_vs1 === MEM_vd) && (MEM_vd =/= 0.U(5.W)) 
  is_D_vs2_W_vd_overlap := is_D_use_vs2 && is_W_use_rd && (ID_vs2 === WB_vd) && (WB_vd =/= 0.U(5.W)) 
  is_D_vs2_E_vd_overlap := is_D_use_vs2 && is_E_use_rd && (ID_vs2 === EXE_vd) && (EXE_vd =/= 0.U(5.W)) 
  is_D_vs2_M_vd_overlap := is_D_use_vs2 && is_M_use_rd && (ID_vs2 === MEM_vd) && (MEM_vd =/= 0.U(5.W)) 
   
  val v_Flush = WireDefault(is_D_vs1_W_vd_overlap || is_D_vs1_E_vd_overlap || is_D_vs1_M_vd_overlap || is_D_vs2_W_vd_overlap || is_D_vs2_E_vd_overlap || is_D_vs2_M_vd_overlap)
  // Control signal - Stall
  // Stall for Data Hazard
  io.Stall_WB_ID_DH := (is_D_rs1_W_rd_overlap || is_D_rs2_W_rd_overlap || is_D_vs1_W_vd_overlap || is_D_vs2_W_vd_overlap)
  io.Stall_EXE_ID_DH := (is_D_rs1_E_rd_overlap || is_D_rs2_E_rd_overlap || is_D_vs1_E_vd_overlap || is_D_vs2_E_vd_overlap)
  io.Stall_MEM_ID_DH := (is_D_rs1_M_rd_overlap || is_D_rs2_M_rd_overlap || is_D_vs1_M_vd_overlap || is_D_vs2_M_vd_overlap)

  io.Stall_MA := Mux(!io.DM_Valid && (MEM_opcode === OPVL || MEM_opcode === OPVS || MEM_opcode === LOAD || MEM_opcode === STORE), true.B, false.B)  // Stall for Waiting Memory Access
  // Control signal - Flush
  io.Flush_BH := Predict_Miss
  io.Flush_WB_ID_DH := (is_D_rs1_W_rd_overlap || is_D_rs2_W_rd_overlap || is_D_rs1_E_rd_overlap || is_D_rs2_E_rd_overlap || is_D_rs1_M_rd_overlap || is_D_rs2_M_rd_overlap || v_Flush)


  // Control signal - Data Forwarding (Bonus)

  /****************** Data Hazard End******************/
}
