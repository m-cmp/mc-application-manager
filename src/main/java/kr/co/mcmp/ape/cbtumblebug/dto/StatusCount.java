package kr.co.mcmp.ape.cbtumblebug.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Status count information")
public class StatusCount {
    @Schema(description = "Total count")
    private int countTotal;

    @Schema(description = "Creating count")
    private int countCreating;

    @Schema(description = "Running count")
    private int countRunning;

    @Schema(description = "Failed count")
    private int countFailed;

    @Schema(description = "Suspended count")
    private int countSuspended;

    @Schema(description = "Rebooting count")
    private int countRebooting;

    @Schema(description = "Terminated count")
    private int countTerminated;

    @Schema(description = "Suspending count")
    private int countSuspending;

    @Schema(description = "Resuming count")
    private int countResuming;

    @Schema(description = "Terminating count")
    private int countTerminating;

    @Schema(description = "Undefined count")
    private int countUndefined;
}