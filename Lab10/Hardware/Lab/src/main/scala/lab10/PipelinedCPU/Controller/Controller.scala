<<<<<<< HEAD
package lab10.PiplinedCPU.Controller

import chisel3._
import chisel3.util._

import lab10.PiplinedCPU.opcode_map._
import lab10.PiplinedCPU.condition._
import lab10.PiplinedCPU.inst_type._
import lab10.PiplinedCPU.alu_op_map._
import lab10.PiplinedCPU.pc_sel_map._
import lab10.PiplinedCPU.wb_sel_map._
import lab10.PiplinedCPU.forwarding_sel_map._
import lab10.PiplinedCPU.vector_ALU_op._
import lab10.PiplinedCPU.wide._

class Controller(memAddrWidth: Int) extends Module {
  val io = IO(new Bundle {
    // Memory control signal interface
    val IM_Mem_R = Output(Bool()) 
    val IM_Mem_W = Output(Bool()) 
    val IM_Length = Output(UInt(4.W))
    val IM_Valid = Input(Bool()) 

    val DM_Mem_R = Output(Bool()) 
    val DM_Mem_W = Output(Bool()) 
    val DM_Length = Output(UInt(4.W))
    val DM_Valid = Input(Bool()) 

    // branch Comp.
    val E_BrEq = Input(Bool())
    val E_BrLT = Input(Bool())

    // Branch Prediction
    val E_Branch_taken = Output(Bool())
    val E_En = Output(Bool())


    val ID_pc = Input(UInt(memAddrWidth.W))
    val EXE_target_pc = Input(UInt(memAddrWidth.W))

    // Flush
    val Flush_WB_ID_DH = Output(Bool()) //TBD
    val Flush_BH = Output(Bool()) // branch hazard flush

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

    val Hcf = Output(Bool())
    
    // Edit for Vector Extension
    val vector_E_ALUSel = Output(UInt(4.W))
    val vector_E_ASel = Output(Bool())
    val vector_E_BSel = Output(UInt(2.W))
    val vector_W_WBSel = Output(UInt(2.W))
    val vector_W_RegWEn = Output(Bool())

    // for AXI sign/unsign load determination
    val wide = Output(UInt(4.W))
  })
  // Inst Decode for each stage 
  val IF_opcode = io.IF_Inst(6, 0)

  val ID_opcode = io.ID_Inst(6, 0)
  val ID_rs1 = io.ID_Inst(19,15)
  val ID_rs2 = io.ID_Inst(24,20)

  val EXE_opcode = io.EXE_Inst(6, 0)
  val EXE_funct3 = io.EXE_Inst(14, 12)
  val EXE_funct7 = io.EXE_Inst(31, 25)
  val EXE_rd     = io.EXE_Inst(11, 7)

  val MEM_opcode = io.MEM_Inst(6, 0)
  val MEM_funct3 = io.MEM_Inst(14, 12)
  val MEM_rd     = io.MEM_Inst(11, 7)

  val WB_opcode = io.WB_Inst(6, 0)
  val WB_rd = io.WB_Inst(11, 7)


  // Control signal - Branch/Jump
  // To Be Modified
  val E_En = Wire(Bool())
  E_En := (EXE_opcode===BRANCH || EXE_opcode===JAL || EXE_opcode===JALR) 

  // val J_En = Wire(Bool())
  // J_En := (EXE_opcode===JAL || EXE_opcode=== JALR)
  // val J_En = Wire(Bool())
  // J_En := (EXE_opcode===JAL || EXE_opcode=== JALR)
  
  // To Be Modified
  val E_Branch_taken = Wire(Bool())
  E_Branch_taken := MuxLookup(EXE_opcode, false.B, Seq(
          BRANCH -> MuxLookup(EXE_funct3, false.B, Seq(
            "b000".U(3.W) -> io.E_BrEq.asUInt,  // beq
            "b001".U(3.W) -> ~io.E_BrEq.asUInt, // bne
            "b100".U(3.W) -> io.E_BrLT.asUInt,  // blt
            "b101".U(3.W) -> ~io.E_BrLT.asUInt, // bge
            "b110".U(3.W) -> io.E_BrLT.asUInt,  // bltu
            "b111".U(3.W) -> ~io.E_BrLT.asUInt, // bgeu
          )),
          JAL    -> true.B,
          JALR   -> true.B,
        ))    
        
  io.E_En := E_En
  io.E_Branch_taken := E_Branch_taken

  // pc predict miss signal
  val Predict_Miss = Wire(Bool())
  Predict_Miss := (E_En && E_Branch_taken && io.ID_pc=/=io.EXE_target_pc)
  // E_En                        -> to make sure opcode = jump/branch
  // E_Branch_taken              -> make sure branch condition is true
  // io.ID_pc=/=io.EXE_target_pc -> or else the jump/branch is useless
  
  // Control signal - Branch Prediction (Lab12)
  val BP_En = Wire(Bool()) // Branch Predict Enable
  BP_En := (IF_opcode===BRANCH)    // To Be Modified

  // Control signal - PC
  when(Predict_Miss){
    io.PCSel := EXE_T_PC
  }.otherwise{
    io.PCSel := IF_PC_PLUS_4
  }

  // Control signal - Branch comparator
  io.E_BrUn := (io.EXE_Inst(13) === 1.U)

  // Control signal - Immediate generator
  // To Be Modified
  io.D_ImmSel := MuxLookup(ID_opcode, 0.U, Seq(
    OP_IMM -> I_type,
    LOAD   -> I_type,
    STORE  -> S_type,
    BRANCH -> B_type,
    LUI    -> U_type,
    AUIPC  -> U_type,
    JAL    -> J_type,
    JALR   -> I_type,
    // BRANCH don't care.
  )) 

  // Control signal - Scalar ALU
  // To Be Modified
  io.E_ASel := MuxLookup(EXE_opcode, 0.U, Seq(
    BRANCH -> 1.U, // selecting pc
    LUI    -> 2.U,
    AUIPC  -> 1.U,
    JAL    -> 1.U,
  ))    
  
  // To Be Modified
  io.E_BSel := MuxLookup(EXE_opcode, 0.U, Seq(
    OP_IMM -> 1.U,
    LUI    -> 1.U,
    AUIPC  -> 1.U,
    JAL    -> 1.U,
    JALR   -> 1.U,
    BRANCH -> 1.U, // selecting sext(offset)
    LOAD   -> 1.U,
    STORE  -> 1.U,
  )) 
  
  // To Be Modified
  io.E_ALUSel := MuxLookup(EXE_opcode, (Cat(0.U(7.W), "b11111".U, 0.U(3.W))), Seq(
    OP     -> (Cat(EXE_funct7, "b11111".U, EXE_funct3)),
    OP_IMM ->    MuxLookup(EXE_funct3, Cat(0.U(7.W), "b11111".U, EXE_funct3), Seq(
      "b101".U(3.W) -> Cat(EXE_funct7, "b11111".U, EXE_funct3),
    ))
  )) 

  // Control signal - Data Memory
  io.DM_Mem_R := (MEM_opcode===LOAD || MEM_opcode===OPVL)
  io.DM_Mem_W := (MEM_opcode===STORE || MEM_opcode===OPVS)

  // (Hsuan) Length for vector load/store must be set > 4 (trigger burst mode) 
  io.DM_Length := Mux(MEM_opcode===OPVS||MEM_opcode===OPVL, 6.U(4.W), Cat(0.U(1.W),MEM_funct3))  // length
  
  // Control signal - Inst Memory
  io.IM_Mem_R := true.B // always true
  io.IM_Mem_W := false.B // always false
  io.IM_Length := "b0010".U // always load a word(inst)

  // Control signal - Scalar Write Back
  // To Be Modified
  io.W_RegWEn := MuxLookup(WB_opcode, false.B, Seq(
    OP     -> true.B, 
    OP_IMM -> true.B,
    LUI    -> true.B,
    AUIPC  -> true.B,
    LOAD   -> true.B,
    JAL    -> true.B,
    JALR   -> true.B,
  ))  

  // To Be Modified
  io.W_WBSel := MuxLookup(WB_opcode, ALUOUT, Seq(
    LOAD -> LD_DATA,
    JAL  -> PC_PLUS_4, 
    JALR -> PC_PLUS_4,
  )) 

  // Control signal - Others
  io.Hcf := (IF_opcode === HCF)
  

  // for AXI unsign/sign load determination
  // default read a word
  io.wide := MuxLookup(MEM_funct3, "b0010".U(4.W), Seq(
    "b000".U(3.W) -> Byte,
    "b001".U(3.W) -> Half,
    "b010".U(3.W) -> Word,
    "b100".U(3.W) -> UByte,
    "b101".U(3.W) -> UHalf,
  ))


  /* lab 10-4 Vector extension */ // To Be Modified
  io.vector_E_ALUSel := MuxLookup(EXE_opcode, VADD_VV, Seq(
    OPIV -> Cat(0.U(1.W),EXE_funct3),
  ))
  io.vector_E_ASel   := MuxLookup(EXE_opcode, false.B, Seq(
                OPIV -> MuxLookup(EXE_funct3, false.B, Seq(
                  "b000".U(3.W) -> false.B,  // for OPIVV
                  "b100".U(3.W) -> false.B,  // for OPIVX
                )),
                OPVL -> false.B,
                OPVS -> false.B,
              ))
  io.vector_E_BSel   := MuxLookup(EXE_opcode, 0.U, Seq(
                OPIV -> MuxLookup(EXE_funct3, 0.U, Seq(
                  "b100".U(3.W) -> 1.U,    // for OPIVX
                )),
                OPVL -> 2.U,
                OPVS -> 2.U,
              ))          

  io.vector_W_RegWEn := (WB_opcode===OPIV || WB_opcode===OPVL)
  io.vector_W_WBSel  := MuxLookup(WB_opcode, 0.U, Seq(
                OPVL -> 1.U, // VLSE_8 select v_ld_data, otherwise select v_alu
              ))



  /****************** Data Hazard ******************/
  // Use rs in ID stage 
  val is_D_use_rs1 = Wire(Bool()) 
  val is_D_use_rs2 = Wire(Bool())
  is_D_use_rs1 := MuxLookup(ID_opcode,false.B,Seq(
    BRANCH -> true.B,
    OP_IMM -> true.B,
    OP     -> true.B,
    JALR   -> true.B,
    LOAD   -> true.B,
    STORE  -> true.B,
    OPVL   -> true.B,
  ))   // To Be Modified
  is_D_use_rs2 := MuxLookup(ID_opcode,false.B,Seq(
    BRANCH -> true.B,
    OP     -> true.B,
    STORE  -> true.B,
    OPIV   -> true.B, // (Hsuan) modified for vmul_vx
  ))   // To Be Modified

  
  // (Hsuan) Use vrs in ID stage 
  val is_D_use_vrs1 = Wire(Bool()) 
  val is_D_use_vrs2 = Wire(Bool())
  is_D_use_vrs1 := MuxLookup(ID_opcode,false.B,Seq(
    OPIV    -> true.B,
    OPVS    -> true.B,
    OPVL    -> true.B,
  )) 
  is_D_use_vrs2 := MuxLookup(ID_opcode,false.B,Seq(
    OPIV    -> true.B,
  )) 


  // Use rd in WB/MEM/EXE stage
  val is_W_use_rd = Wire(Bool())
  is_W_use_rd := MuxLookup(WB_opcode,false.B,Seq(
    OP_IMM -> true.B,
    OP     -> true.B,
    AUIPC  -> true.B,
    LUI    -> true.B,
    JAL    -> true.B,
    JALR   -> true.B,
    LOAD   -> true.B,
  ))   // To Be Modified

  val is_M_use_rd = Wire(Bool())
  is_M_use_rd := MuxLookup(MEM_opcode,false.B,Seq(
    OP_IMM -> true.B,
    OP     -> true.B,
    AUIPC  -> true.B,
    LUI    -> true.B,
    JAL    -> true.B,
    JALR   -> true.B,
    LOAD   -> true.B,
  ))   // To Be Modified

  val is_EXE_use_rd = Wire(Bool())
  is_EXE_use_rd := MuxLookup(EXE_opcode,false.B,Seq(
    OP_IMM -> true.B,
    OP     -> true.B,
    AUIPC  -> true.B,
    LUI    -> true.B,
    JAL    -> true.B,
    JALR   -> true.B,
    LOAD   -> true.B,
  ))   // To Be Modified

  // Use vrd in WB/MEM/EXE stage
  val is_W_use_vrd = Wire(Bool())
  is_W_use_vrd := MuxLookup(WB_opcode,false.B,Seq(
    OPIV -> true.B,
    OPVL -> true.B,
  ))   // To Be Modified

  val is_M_use_vrd = Wire(Bool())
  is_M_use_vrd := MuxLookup(MEM_opcode,false.B,Seq(
    OPIV -> true.B,
    OPVL -> true.B,
  ))   // To Be Modified

  val is_EXE_use_vrd = Wire(Bool())
  is_EXE_use_vrd := MuxLookup(EXE_opcode,false.B,Seq(
    OPIV -> true.B,
    OPVL -> true.B,
  ))   // To Be Modified

  // Hazard condition (rd, rs overlap)
  val is_D_rs1_W_rd_overlap = Wire(Bool())
  val is_D_rs2_W_rd_overlap = Wire(Bool())

  is_D_rs1_W_rd_overlap := is_D_use_rs1 && is_W_use_rd && (ID_rs1 === WB_rd) && (WB_rd =/= 0.U(5.W))
  is_D_rs2_W_rd_overlap := is_D_use_rs2 && is_W_use_rd && (ID_rs2 === WB_rd) && (WB_rd =/= 0.U(5.W))
 
  // Hazard condition (rd, rs overlap)
  val is_D_rs1_M_rd_overlap = Wire(Bool())
  val is_D_rs2_M_rd_overlap = Wire(Bool())

  is_D_rs1_M_rd_overlap := is_D_use_rs1 && is_M_use_rd && (ID_rs1 === MEM_rd) && (MEM_rd =/= 0.U(5.W))
  is_D_rs2_M_rd_overlap := is_D_use_rs2 && is_M_use_rd && (ID_rs2 === MEM_rd && (MEM_rd =/= 0.U(5.W)))

  // Hazard condition (rd, rs overlap)
  val is_D_rs1_E_rd_overlap = Wire(Bool())
  val is_D_rs2_E_rd_overlap = Wire(Bool())

  is_D_rs1_E_rd_overlap := is_D_use_rs1 && is_EXE_use_rd && (ID_rs1 === EXE_rd) && (EXE_rd =/= 0.U(5.W))
  is_D_rs2_E_rd_overlap := is_D_use_rs2 && is_EXE_use_rd && (ID_rs2 === EXE_rd) && (EXE_rd =/= 0.U(5.W))

  // Hazard condition (vrd, vrs overlap)
  val is_D_vrs1_W_vrd_overlap = Wire(Bool())
  val is_D_vrs2_W_vrd_overlap = Wire(Bool())

  is_D_vrs1_W_vrd_overlap := is_D_use_vrs1 && is_W_use_vrd && (ID_rs1 === WB_rd) && (WB_rd =/= 0.U(5.W))
  is_D_vrs2_W_vrd_overlap := is_D_use_vrs2 && is_W_use_vrd && (ID_rs2 === WB_rd) && (WB_rd =/= 0.U(5.W))
 
  // Hazard condition (vrd, vrs overlap)
  val is_D_vrs1_M_vrd_overlap = Wire(Bool())
  val is_D_vrs2_M_vrd_overlap = Wire(Bool())

  is_D_vrs1_M_vrd_overlap := is_D_use_vrs1 && is_M_use_vrd && (ID_rs1 === MEM_rd) && (MEM_rd =/= 0.U(5.W))
  is_D_vrs2_M_vrd_overlap := is_D_use_vrs2 && is_M_use_vrd && (ID_rs2 === MEM_rd && (MEM_rd =/= 0.U(5.W)))

  // Hazard condition (vrd, vrs overlap)
  val is_D_vrs1_E_vrd_overlap = Wire(Bool())
  val is_D_vrs2_E_vrd_overlap = Wire(Bool())

  is_D_vrs1_E_vrd_overlap := is_D_use_vrs1 && is_EXE_use_vrd && (ID_rs1 === EXE_rd) && (EXE_rd =/= 0.U(5.W))
  is_D_vrs2_E_vrd_overlap := is_D_use_vrs2 && is_EXE_use_vrd && (ID_rs2 === EXE_rd) && (EXE_rd =/= 0.U(5.W))


  // Control signal - Stall for Scalar Operation
  io.Stall_WB_ID_DH :=  (is_D_rs1_W_rd_overlap || is_D_rs2_W_rd_overlap || is_D_vrs1_W_vrd_overlap || is_D_vrs2_W_vrd_overlap) // Stall for Data Hazard
  io.Stall_MEM_ID_DH := (is_D_rs1_M_rd_overlap || is_D_rs2_M_rd_overlap || is_D_vrs1_M_vrd_overlap || is_D_vrs2_M_vrd_overlap)
  io.Stall_EXE_ID_DH := (is_D_rs1_E_rd_overlap || is_D_rs2_E_rd_overlap || is_D_vrs1_E_vrd_overlap || is_D_vrs2_E_vrd_overlap)


  // io.Stall_MA := (io.DM_Mem_R && ~io.DM_Valid) || (io.DM_Mem_W && ~io.DM_Valid)  // Stall for Waiting Memory Access
  
  io.Stall_MA := ~io.DM_Valid && (MEM_opcode===OPVS || MEM_opcode===OPVL || MEM_opcode===LOAD || MEM_opcode===STORE)
  // Control signal - Flush
  io.Flush_WB_ID_DH := ((is_D_rs1_W_rd_overlap || is_D_rs2_W_rd_overlap) 
                     || (is_D_rs1_M_rd_overlap || is_D_rs2_M_rd_overlap) 
                     || (is_D_rs1_E_rd_overlap || is_D_rs2_E_rd_overlap)
                     || (is_D_vrs1_W_vrd_overlap || is_D_vrs2_W_vrd_overlap)
                     || (is_D_vrs1_M_vrd_overlap || is_D_vrs2_M_vrd_overlap)
                     || (is_D_vrs1_E_vrd_overlap || is_D_vrs2_E_vrd_overlap))
  io.Flush_BH := Predict_Miss 

  // Control signal - Data Forwarding (Bonus)

  /****************** Data Hazard End******************/
}
=======
package lab10.PiplinedCPU.Controller

import chisel3._
import chisel3.util._

import lab10.PiplinedCPU.opcode_map._
import lab10.PiplinedCPU.condition._
import lab10.PiplinedCPU.inst_type._
import lab10.PiplinedCPU.alu_op_map._
import lab10.PiplinedCPU.pc_sel_map._
import lab10.PiplinedCPU.wb_sel_map._
import lab10.PiplinedCPU.forwarding_sel_map._

class Controller(memAddrWidth: Int) extends Module {
  val io = IO(new Bundle {
    // Memory control signal interface
    val IM_Mem_R = Output(Bool()) 
    val IM_Mem_W = Output(Bool()) 
    val IM_Length = Output(UInt(4.W))
    val IM_Valid = Input(Bool()) 

    val DM_Mem_R = Output(Bool()) 
    val DM_Mem_W = Output(Bool()) 
    val DM_Length = Output(UInt(4.W))
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

    val Hcf = Output(Bool())
  })
  // Inst Decode for each stage 
  val IF_opcode = io.IF_Inst(6, 0)

  val ID_opcode = io.ID_Inst(6, 0)
  val ID_rs1 = io.ID_Inst(19,15)
  val ID_rs2 = io.ID_Inst(24,20)

  val EXE_opcode = io.EXE_Inst(6, 0)
  val EXE_funct3 = io.EXE_Inst(14, 12)
  val EXE_funct7 = io.EXE_Inst(31, 25)

  val MEM_opcode = io.MEM_Inst(6, 0)
  val MEM_funct3 = io.MEM_Inst(14, 12)


  val WB_opcode = io.WB_Inst(6, 0)
  val WB_rd = io.WB_Inst(11, 7)


  // Control signal - Branch/Jump
  val E_En = Wire(Bool())
  E_En := (EXE_opcode===BRANCH)         // To Be Modified
  val E_Branch_taken = Wire(Bool())
  E_Branch_taken := MuxLookup(EXE_opcode, false.B, Seq(
          BRANCH -> MuxLookup(EXE_funct3, false.B, Seq(
            "b000".U(3.W) -> io.E_BrEq.asUInt,
          )),
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
    OP_IMM -> I_type,
    LOAD -> I_type,
    BRANCH -> B_type,
    LUI -> U_type,
  )) // To Be Modified

  // Control signal - Scalar ALU
  io.E_ASel := MuxLookup(EXE_opcode, 0.U, Seq(
    BRANCH -> 1.U,
    LUI -> 2.U,
  ))    // To Be Modified
  io.E_BSel := 1.U // To Be Modified
  
  io.E_ALUSel := MuxLookup(EXE_opcode, (Cat(0.U(7.W), "b11111".U, 0.U(3.W))), Seq(
    OP -> (Cat(EXE_funct7, "b11111".U, EXE_funct3)),
    OP_IMM -> (Cat(0.U(7.W), "b11111".U, EXE_funct3))
  )) // To Be Modified

  // Control signal - Data Memory
  io.DM_Mem_R := (MEM_opcode===LOAD)
  io.DM_Mem_W := (MEM_opcode===STORE)
  io.DM_Length := Cat(0.U(1.W),MEM_funct3) // length

  // Control signal - Inst Memory
  io.IM_Mem_R := true.B // always true
  io.IM_Mem_W := false.B // always false
  io.IM_Length := "b0010".U // always load a word(inst)

  // Control signal - Scalar Write Back
  io.W_RegWEn := MuxLookup(WB_opcode, false.B, Seq(
    OP_IMM -> true.B,
    LOAD -> true.B,
    LUI -> true.B,
  ))  // To Be Modified

  
  io.W_WBSel := MuxLookup(WB_opcode, ALUOUT, Seq(
    LOAD -> LD_DATA,
  )) // To Be Modified

  // Control signal - Others
  io.Hcf := (IF_opcode === HCF)

  /****************** Data Hazard ******************/
  // Use rs in ID stage 
  val is_D_use_rs1 = Wire(Bool()) 
  val is_D_use_rs2 = Wire(Bool())
  is_D_use_rs1 := MuxLookup(ID_opcode,false.B,Seq(
    BRANCH -> true.B,
    OP_IMM -> true.B,
    OP     -> true.B,
    JALR   -> true.B,
    LOAD   -> true.B,
    STORE  -> true.B,
  ))   // To Be Modified
  is_D_use_rs2 := MuxLookup(ID_opcode,false.B,Seq(
    BRANCH -> true.B,
    OP     -> true.B,
    STORE  -> true.B,
  ))   // To Be Modified

  // Use rd in WB stage
  val is_W_use_rd = Wire(Bool())
  is_W_use_rd := MuxLookup(WB_opcode,false.B,Seq(
    OP_IMM -> true.B,
    OP     -> true.B,
    AUIPC  -> true.B,
    LUI    -> true.B,
    JAL    -> true.B,
    JALR   -> true.B,
    LOAD   -> true.B,
  ))   // To Be Modified

  val is_M_use_rd = Wire(Bool())
  is_M_use_rd := MuxLookup(MEM_opcode,false.B,Seq(
    OP_IMM -> true.B,
    OP     -> true.B,
    AUIPC  -> true.B,
    LUI    -> true.B,
    JAL    -> true.B,
    JALR   -> true.B,
    LOAD   -> true.B,
  ))   // To Be Modified

  val is_EXE_use_rd = Wire(Bool())
  is_EXE_use_rd := MuxLookup(EXE_opcode,false.B,Seq(
    OP_IMM -> true.B,
    OP     -> true.B,
    AUIPC  -> true.B,
    LUI    -> true.B,
    JAL    -> true.B,
    JALR   -> true.B,
    LOAD   -> true.B,
  ))   // To Be Modified

  // Hazard condition (rd, rs overlap)
  val is_D_rs1_W_rd_overlap = Wire(Bool())
  val is_D_rs2_W_rd_overlap = Wire(Bool())

  is_D_rs1_W_rd_overlap := is_D_use_rs1 && is_W_use_rd && (ID_rs1 === WB_rd) && (WB_rd =/= 0.U(5.W))
  is_D_rs2_W_rd_overlap := is_D_use_rs2 && is_W_use_rd && (ID_rs2 === WB_rd) && (WB_rd =/= 0.U(5.W))
 
  // Hazard condition (rd, rs overlap)
  val is_D_rs1_M_rd_overlap = Wire(Bool())
  val is_D_rs2_M_rd_overlap = Wire(Bool())

  is_D_rs1_M_rd_overlap := is_D_use_rs1 && is_M_use_rd && (ID_rs1 === MEM_rd) && (MEM_rd =/= 0.U(5.W))
  is_D_rs2_M_rd_overlap := is_D_use_rs2 && is_M_use_rd && (ID_rs2 === MEM_rd && (MEM_rd =/= 0.U(5.W)))

  // Hazard condition (rd, rs overlap)
  val is_D_rs1_E_rd_overlap = Wire(Bool())
  val is_D_rs2_E_rd_overlap = Wire(Bool())

  is_D_rs1_E_rd_overlap := is_D_use_rs1 && is_EXE_use_rd && (ID_rs1 === EXE_rd) && (EXE_rd =/= 0.U(5.W))
  is_D_rs2_E_rd_overlap := is_D_use_rs2 && is_EXE_use_rd && (ID_rs2 === EXE_rd) && (EXE_rd =/= 0.U(5.W))


  // Control signal - Stall
  io.Stall_WB_ID_DH :=  (is_D_rs1_W_rd_overlap || is_D_rs2_W_rd_overlap) // Stall for Data Hazard
  io.Stall_MEM_ID_DH := (is_D_rs1_M_rd_overlap || is_D_rs2_M_rd_overlap)
  io.Stall_EXE_ID_DH := (is_D_rs1_E_rd_overlap || is_D_rs2_E_rd_overlap)

  io.Stall_MA := (io.DM_Mem_R && ~io.DM_Valid) || (io.DM_Mem_W && ~io.DM_Valid)  // Stall for Waiting Memory Access

  // Control signal - Flush
  io.Flush_WB_ID_DH := ((is_D_rs1_W_rd_overlap || is_D_rs2_W_rd_overlap) 
                     || (is_D_rs1_M_rd_overlap || is_D_rs2_M_rd_overlap) 
                     || (is_D_rs1_E_rd_overlap || is_D_rs2_E_rd_overlap))
  io.Flush_BH := Predict_Miss 

  // Control signal - Data Forwarding (Bonus)

  /****************** Data Hazard End******************/
}
>>>>>>> 301bdb3 (Finished Prepare Lab10-2)
