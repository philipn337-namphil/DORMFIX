package com.dormfix.location.application;

public record CreateSpaceCommand(String code, String name, String type, int floor, String description) {
}
