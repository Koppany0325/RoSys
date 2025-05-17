package com.example.rosys.entities;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Region {
    int value;
    List<int[]> pixels = new ArrayList<>();
    double avgOriginalValue;
}
