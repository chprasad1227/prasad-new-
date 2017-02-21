package com.aexp.cicdmainframe.hpuftintegration.exception;

public enum HPUFTError {

	DOMAIN_NULL("UFT001","Domain name is NULL"),
	PROJECT_NULL("UFT002","Project name is NULL"),
	BVSID_NULL("UFT003","BvsId  is NULL"),
	BVSRUNID_NULL("UFT004","BvsRunId is NULL"),
	INVALID("UFT005", "Username is NULL"),
	INVALID("UFT006","Password is NULL"),
	INVALID("UFT007","SSL Certificate is NULL"),
	INVALID("UFT008","SSL password is NULL"),
	INVALID("UFT009", "URL is NULL");
	
	private String erroCode;
	private String errorMessage;

    HPUFTError(String erroCode,String errorMessage) {
    	this.erroCode = erroCode;
        this.errorMessage = errorMessage;
    }
    
    public String getErroCode() {
        return errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
