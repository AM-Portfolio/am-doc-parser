package org.am.mypotrfolio.domain.common;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAsset {

    @JsonProperty("Name")
    @JsonAlias({"Name", "prop1"})
    private String name;

    @JsonProperty("ISIN")
    @JsonAlias({"isin", "ISIN"})
    private String isin;

    @JsonProperty("Symbol")
    @JsonAlias({"Symbol", "prop1"})
    private String symbol;

    @JsonProperty("Quantity")
    @JsonAlias({"Quantity", "Quantity Available"})
    private String quantity;

    @JsonProperty("Average Price")
    @JsonAlias({"Avg. Cost", "Avg Price", "Average Price"})
    private String avgPrice;

    @JsonProperty("Investment")
    @JsonAlias({"Invested Value", "Investment"})
    private String investmentValue;

}
