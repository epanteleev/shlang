
extern int printf(char format[], ...);

typedef struct Vect_ {
    char x;
    char y;
    char z;
    char w;
    char v;
} Vect;

Vect getVect(Vect v) {
    Vect v;
    v.x = 1;
    v.y = 2;
    v.z = 3;
    v.w = 4;
    v.v = 5;
    return v;
}

int main() {
    Vect v = getVect();
    printf("x: %d y: %d z: %d w: %d v: %d\n", v.x, v.y, v.z, v.w, v.v);
    return 0;
}