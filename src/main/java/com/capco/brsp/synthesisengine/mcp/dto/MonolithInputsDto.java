package com.capco.brsp.synthesisengine.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonolithInputsDto {
    private String dataMatrixUrl;           // URL to data_matrix.csv
    private String adjacencyMatrixUrl;      // URL to adjacency_matrix.csv
    private String graphPerformsCallsUrl;   // URL to graph_performs_calls.svg
}
