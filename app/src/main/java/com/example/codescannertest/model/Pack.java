package com.example.codescannertest.model;

import androidx.annotation.NonNull;

public class Pack {
    public String name;
    public int counterRecord = 0;
    public boolean isSelected = false;
    public boolean isVisibleRadioButton = false;
    public boolean isWritingPackage = false;

    public Pack(String name, boolean isWritingPackage){
        this.name = name;
        this.isWritingPackage = isWritingPackage;
    }

    public Pack(Pack p) {
        this.name = p.name;
        this.isWritingPackage = p.isWritingPackage;
        this.counterRecord = p.counterRecord;
        this.isSelected = p.isSelected;
        this.isVisibleRadioButton = p.isVisibleRadioButton;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Pack)) return false;
        final Pack that = (Pack) obj;
        return this.name.equals(that.name)
                && this.isWritingPackage == that.isWritingPackage
                && this.counterRecord == that.counterRecord;
    }

    @NonNull
    @Override
    public String toString() {
        return "{" + "name: " + name + ";"
                + "counterRecords: " + counterRecord + ";"
                /*+ "isSelected: " + isSelected + ";"
                + "isVisibleRadioButton: " + isVisibleRadioButton + ";"*/
                + "isWritingPackage: " + isWritingPackage + "}";
    }
}
