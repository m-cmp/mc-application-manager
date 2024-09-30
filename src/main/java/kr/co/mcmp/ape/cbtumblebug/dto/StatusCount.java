package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Status count information")
public class StatusCount {
    @ApiModelProperty(value = "Total count")
    private int countTotal;

    @ApiModelProperty(value = "Creating count")
    private int countCreating;

    @ApiModelProperty(value = "Running count")
    private int countRunning;

    @ApiModelProperty(value = "Failed count")
    private int countFailed;

    @ApiModelProperty(value = "Suspended count")
    private int countSuspended;

    @ApiModelProperty(value = "Rebooting count")
    private int countRebooting;

    @ApiModelProperty(value = "Terminated count")
    private int countTerminated;

    @ApiModelProperty(value = "Suspending count")
    private int countSuspending;

    @ApiModelProperty(value = "Resuming count")
    private int countResuming;

    @ApiModelProperty(value = "Terminating count")
    private int countTerminating;

    @ApiModelProperty(value = "Undefined count")
    private int countUndefined;
}