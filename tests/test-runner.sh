#!/bin/bash

RED=`tput setaf 1`
GREEN=`tput setaf 2`
RESET=`tput sgr0`

JAVA=java

export JAVA_OPTS="-ea"
IR_COMPILER="../build/ssa-1.0/bin/ssa"

mkdir ../build
unzip -o ../ssa/build/distributions/ssa-1.0.zip -d ../build

function compile_test() {
	${IR_COMPILER} "$1.ir"
        gcc "$1/base.S" runtime.c -o "$1/base"
        gcc "$1/opt.S" runtime.c -o "$1/opt"
}

function run_test() {
	echo "${GREEN}[Run base: $1]${RESET}"
	BASE_RESULT=$(./$1/base)
	check $1 "$2" "$BASE_RESULT"

	echo "[Run opt: $1]"
	OPT_RESULT=$(./$1/opt)
	check $1 "$2" "$OPT_RESULT"
}

function check() {
	result=$3
	expected=$2
	if [ "$result" == "$expected" ];
	then
  		echo -e "\t${GREEN}[SUCCESS] '$result'${RESET}"
	else
  		echo -e "\t${RED}[FAIL]: '$1'${RESET}"
  		echo -e "\t${GREEN}[Expected]: '$expected'${RESET}"
  		echo -e "\t${RED}[Actual]:${RESET} '$result'"
	fi
}

function compile_and_run() {
	local test_name=$1
	local expected_result=$2
	compile_test "$test_name"
	run_test "$test_name" "$expected_result"
}

compile_and_run manyArguments 36
compile_and_run manyArguments1 36.000000
compile_and_run sum 16
compile_and_run sum1 16.000000
compile_and_run fib 21
compile_and_run fib_opt 21
compile_and_run fib_recursive 21
compile_and_run discriminant -192
compile_and_run discriminant1 -192.000000
compile_and_run factorial 40320
compile_and_run manyBranches "7
0"
compile_and_run clamp "9
10
8"
compile_and_run fill_in_array0 "01234"
compile_and_run fill_in_array1 "01234"
compile_and_run fill_in_array2 "01234"
compile_and_run fill_in_array3 "0123456789"
compile_and_run i32_to_i64 "-1"
compile_and_run u32_to_u64 "1"
compile_and_run i64_to_i32 "-1"
compile_and_run hello_world "Hello world"
compile_and_run load_global_var 120
compile_and_run load_global_var1 "abc 120"
compile_and_run load_global_var2 "-8
-16
-32
-64
8
16
32
64"
compile_and_run load_store_global_var "1000"
compile_and_run neg 1
compile_and_run neg1 1.000000
compile_and_run neg2 1.000000
