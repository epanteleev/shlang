#include <inttypes.h>
#include <stdio.h>

int main() {
    int64_t hash = -1; /* init value */
    printf("inverted = %" PRIi64 "\n", ~hash);
    return 0;
}