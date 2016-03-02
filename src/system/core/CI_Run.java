/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package system.core;

/**
 *
 * @author Saladin2020
 */
public abstract class CI_Run implements Iintextor {

    protected Object obj;
    private int count = 1;
    private int mode = 1;

    public void setCount(int count) {
        this.count = count;
    }

    public void setCount(String count) {
        if (count.equals("INFI")) {
            this.mode = 2;
        }
    }

    public CI_Run() {
        setIndex(obj);
        switch (this.mode) {
            case 1:
                int i = 0;
                while (i < count) {
                    //setIndex(obj);
                    index();
                    i++;
                }
                break;
            case 2:
                while (true) {
                    //setIndex(obj);
                    index();
                }
            default:
                break;
        }

    }

}
