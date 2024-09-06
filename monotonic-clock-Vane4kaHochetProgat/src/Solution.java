import org.jetbrains.annotations.NotNull;

/**
 * В теле класса решения разрешено использовать только финальные переменные типа RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author : Murashov Ivan
 */
public class Solution implements MonotonicClock {

//    static class RegularTime {
//        private final RegularInt c1 = new RegularInt(0);
//        private final RegularInt c2 = new RegularInt(0);
//        private final RegularInt c3 = new RegularInt(0);
//
//        void setValue(Time time) {
//            c1.setValue(time.getD1());
//            c2.setValue(time.getD2());
//            c3.setValue(time.getD3());
//        }
//
//        Time getValue() {
//            return new Time(c1.getValue(), c2.getValue(), c3.getValue());
//        }
//
//        int compareTo(Time time) {
//            return this.getValue().compareTo(time);
//        }
//
//        Time getValueForPrint(Time time) {
//            if (this.c1.getValue() < time.getD1()) {
//                return new Time(
//                        this.c1.getValue(),
//                        time.getD2(),
//                        time.getD3()
//                );
//            }
//            if (this.c2.getValue() < time.getD2()) {
//                return new Time(
//                        this.c1.getValue(),
//                        this.c2.getValue(),
//                        time.getD3()
//                );
//            }
//            return this.getValue();
//        }
//
//    }

    private final RegularInt c1 = new RegularInt(0);
    private final RegularInt c2 = new RegularInt(0);
    private final RegularInt c3 = new RegularInt(0);

    private final RegularInt p1 = new RegularInt(0);
    private final RegularInt p2 = new RegularInt(0);
    private final RegularInt p3 = new RegularInt(0);


    @Override
    public void write(@NotNull Time time) {
        if (time.compareTo(new Time(c1.getValue(), c2.getValue(), c3.getValue())) > 0) {
            c1.setValue(time.getD1());
            c2.setValue(time.getD2());
            c3.setValue(time.getD3());
            p3.setValue(time.getD3());
            p2.setValue(time.getD2());
            p1.setValue(time.getD1());
        }

    }

    @NotNull
    @Override
    public Time read() {
        int r11 = p1.getValue();
        int r12 = p2.getValue();
        int r13 = p3.getValue();
        int r23 = c3.getValue();
        int r22 = c2.getValue();
        int r21 = c1.getValue();
        Time r1 = new Time(r11, r12, r13);
        Time r2 = new Time(r21, r22, r23);
        if (r1.compareTo(r2) == 0) {
            return r1;
        } else {
            return new Time(
                    r21,
                    (r11 == r21 ? r22 : 0),
                    (r11 == r21 && r12 == r22 ? r23 : 0)
            );

        }
    }
}
