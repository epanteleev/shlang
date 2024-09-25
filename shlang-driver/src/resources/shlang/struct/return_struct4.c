
extern int printf(char format[], ...);

typedef struct Vect_ {
    char x;
    char y;
    char z;
    char w;
    char v;
    char u;
    char t;
    char s;
} Vect;

Vect getVect() {
    Vect v;
    v.x = 1;
    v.y = 2;
    v.z = 3;
    v.w = 4;
    v.v = 5;
    v.u = 6;
    v.t = 7;
    v.s = 8;
    return v;
}

int main() {
    Vect v = getVect();
    printf("x: %d y: %d z: %d w: %d v: %d u: %d t: %d s: %d\n", v.x, v.y, v.z, v.w, v.v, v.u, v.t, v.s);
    return 0;
}