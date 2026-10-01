package com.dormfix.location.application;

public record UpdateSpaceCommand(String code, String name, String type, int floor, String description) {
}
