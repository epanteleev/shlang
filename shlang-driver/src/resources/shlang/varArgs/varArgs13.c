

typedef struct Point {
    long x;
    long y;
} Point;

typedef struct Vector {
    long x;
    long y;
    long z;
} Vector;

extern int printf(const char *fmt, ...);

void my_printf0(const char *fmt, __builtin_va_list args) {
    Point first;
    first = __builtin_va_arg(args, Point);

    printf("my_printf0: ");
    Vector second = __builtin_va_arg(args, Vector);
    printf(fmt, first.x, first.y, second.x, second.y, second.z);
}

void my_printf(const char *fmt, ...) {
    __builtin_va_list args;
    __builtin_va_start(args, fmt);
    my_printf0(fmt, args);
    __builtin_va_end(args);
}

int main() {
    Point p = {1, 2};
    Vector v = {3, 4, 5};
    my_printf("Point: (%d, %d), Vector: (%ld, %ld, %ld)\n", p, v);
    return 0;
}