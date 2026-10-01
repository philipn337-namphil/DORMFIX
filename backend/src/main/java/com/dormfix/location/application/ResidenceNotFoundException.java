package com.dormfix.location.application;
public class ResidenceNotFoundException extends RuntimeException { public ResidenceNotFoundException(Long id) { super("Residence was not found: " + id); } }
