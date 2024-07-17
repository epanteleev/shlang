
extern void printf(char format[], ...);

typedef struct Point_ {
    int x;
    int y;
} Point;

Point getPoint() {
    Point p;
    p.x = 1;
    p.y = 2;
    return p;
}

int main() {
    Point p = getPoint();
    printf("x: %d, y: %d\n", p.x, p.y);
    return 0;
}