if [ -n "$1" ]
then
        cp ./src/test/scala/acai_lab11/ASMtest/asm/$1.asm ./src/main/resource/inst.asm
        cp ./src/test/scala/acai_lab11/ASMtest/inst/$1.hex ./src/main/resource/inst.hex
        cp ./src/test/scala/acai_lab11/ASMtest/data/$1.hex ./src/main/resource/data.hex
else
    echo "[Error] usage should be: ./test_data.sh <which Test program>"
fi
