package edu.duke.ece.amz;

import lombok.Data;
import edu.duke.ece.amz.proto.WorldAmazon.*;

import java.util.List;

@Data
public class Package {
    public static final String PROCESSING = "processing";
    public static final String PACKING = "packing";
    public static final String PACKED = "packed";
    public static final String LOADING = "loading";
    public static final String LOADED = "loaded";
    public static final String DELIVERING = "delivering";
    public static final String DELIVERED = "delivered";
    public static final String ERROR = "error";

    private long id;
    private int whID;
    private int truckID;
    private int desX;
    private int desY;
    private int whX;
    private int whY;
    private String status;
    private List<AProduct> products;


    public Package(
            long id, int whID,
            int desX, int desY,
            int whX, int whY,
            List<AProduct> products
    ) {
        this.id = id;
        this.whID = whID;
        this.truckID = -1;
        this.desX = desX;
        this.desY = desY;
        this.whX = whX;
        this.whY = whY;
        this.status = PROCESSING;
        this.products = products;
    }


    public void setStatus(String status){
        this.status = status;
        // write the result into DB
//        new SQL().updateStatus(this.id, this.status);
    }
}
