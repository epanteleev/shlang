/* Tail call optimizations would reverse the order of additions in func().  */

extern void abort (void);
extern void exit (int);

double func (const double *array)
{
  double d = *array;
  if (d == 0.0)
    return d;
  else
    return d + func (array + 1);
}

int main ()
{
  double values[] = { 0.1e-100, 1.0, -1.0, 0.0 };
  if (func (values) != 0.1e-100)
    abort ();
  exit (0);
}