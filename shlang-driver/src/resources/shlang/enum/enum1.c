#include <assert.h>

enum color {
    RED,
    GREEN,
    BLUE
};

int main() {
    enum color c = RED;
    assert(c == 0);
    assert(GREEN == 1);
    assert(BLUE == 2);
    assert(1 + RED == GREEN);
    return 0;
}