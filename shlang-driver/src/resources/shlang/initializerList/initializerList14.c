extern int printf(const char* fmt, ...);

char ptr0[] = "b" ; // NULL
char ptr[] = "a" ; // NULL
static char *table[][2] = {
    { ptr0, ptr }
};

int main() {
    printf("%s", *table[0]);
    return 0;
}