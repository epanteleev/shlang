#include <stdio.h>
#include <stdlib.h>

struct DATA {
    long a;
    long b;
    double c;
};

struct DATA data;

void printLong(long value) {
	printf("%ld\n", value);	
}

void printULong(unsigned long value) {
    printf("%lu\n", value);
}

void printInt(int value) {
        printf("%d\n", value); 
}

void printUInt(unsigned int value) {
        printf("%u\n", value);
}

void printShort(short value) {
        printf("%d\n", value);
}

void printUShort(unsigned short value) {
        printf("%u\n", value);
}

void printByte(char value) {
        printf("%d\n", value);
}

void printUByte(unsigned char value) {
        printf("%u\n", value);
}

void printFloat(float fp) {
	printf("%f\n", fp);
}

void printDouble(double fp) {
	printf("%lf\n", fp);
}

void printLongArray(long* array, long size) {
        for (long i = 0; i < size; i++) {
                printf("%ld", array[i]);
        }
        printf("\n");
}

void printIntArray(int* array, int size) {
	for (int i = 0; i < size; i++) {
		printf("%d", array[i]);
	}
	printf("\n");
}

void printByteArray(unsigned char* array, int size) {
	for (int i = 0; i < size; i++) {
		printf("%u ", array[i]);
	}
	printf("\n");
}

void printByteArrayWithSpaces(unsigned char* array, int size) {
        for (int i = 0; i < size; i++) {
                printf("%d ", array[i]);
        }
        printf("\n");
}

void printIntArrayWithSpaces(int* array, int size) {
        for (int i = 0; i < size; i++) {
                printf("%d ", array[i]);
        }
        printf("\n");
}

void printLongArrayWithSpaces(long* array, int size) {
        for (int i = 0; i < size; i++) {
                printf("%d ", array[i]);
        }
        printf("\n");
}

void printFloatArray(float* array, int size) {
        for (int i = 0; i < size; i++) {
                printf("%f ", array[i]);
        }
        printf("\n");
}

void printDoubleArray(double* array, int size) {
        for (int i = 0; i < size; i++) {
                printf("%lf ", array[i]);
        }
        printf("\n");
}

void reportError() {
    printf("Error\n");
    exit(1);
}