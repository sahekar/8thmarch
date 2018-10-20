package com.dvsts.avaya.processing.transformers;

import com.dvsts.avaya.processing.logic.AvayaPacket;
import com.dvsts.avaya.processing.logic.MainComputationModel;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.LocalDateTime;

import static com.dvsts.avaya.processing.AppConfig.db;
import static com.dvsts.avaya.processing.AppConfig.detailsEventTopic;


public class AvayaPacketTransformer implements Transformer<String, GenericRecord, KeyValue<String, GenericRecord>> {
    private ProcessorContext context;
    private KeyValueStore<String,AvayaPacket> kvStore;
    private final AvroTransformer transformer;
    private MainComputationModel mainComputationModel;

    public AvayaPacketTransformer(AvroTransformer transformer, MainComputationModel mainComputationModel) {
        this.transformer = transformer;
        this.mainComputationModel = mainComputationModel;
    }

    @Override
    public void init(ProcessorContext context) {
        this.context = context;
        this.kvStore = (KeyValueStore) context.getStateStore(db);
    }

    @Override
    public KeyValue<String, GenericRecord> transform(String key, GenericRecord value) {

        String ssrc1 =  value.get("ssrc1").toString();
        String ssrc2 = value.get("ssrc2").toString();
        String aggrKey = ssrc1+ssrc2;
        AvayaPacket existKey = this.kvStore.get(aggrKey);

        AvayaPacket result = null;
        final AvayaPacket packet = create(value,"create");

        if(existKey == null) {
             result = mainComputationModel.calculatesCallMetric(packet,new AvayaPacket());
        } else {
             result = mainComputationModel.calculatesCallMetric(packet,existKey);
        }

        this.kvStore.put(aggrKey,result);

        //E   System.out.println("data from store: "+ this.kvStore.get(aggrKey));

        GenericRecord avroResult = transformer.toEventAvroRecord(result,detailsEventTopic);

        return new KeyValue<>(key,avroResult);
    }


    private AvayaPacket create(GenericRecord entry,String status){

        AvayaPacket packet = new AvayaPacket();
        packet.setStatus("active");

        packet.setIp1(entry.get("ip").toString());


        // packet.setIp1( entry.get("ip").toString());
        GenericRecord senderReport = (GenericRecord) entry.get("senderReport");
        GenericRecord appSpecificReport = (GenericRecord) entry.get("appSpecificReport");
        GenericRecord sourceDescription = (GenericRecord) entry.get("sourceDescription");
        GenericRecord receiverReport = (GenericRecord) entry.get("receiverReport");

        packet.setSsrc1(entry.get("ssrc1").toString());
        packet.setSsrc2(entry.get("ssrc2").toString());
        packet.setClientId(entry.get("clientid").toString());


        if (senderReport == null) {
            packet.setJitter(Integer.parseInt(receiverReport.get("jitter").toString()));
            packet.setLoss(Integer.parseInt(receiverReport.get("loss").toString()));
        } else {
            packet.setJitter(Integer.parseInt(senderReport.get("jitter").toString()));
            packet.setLoss(Integer.parseInt(senderReport.get("loss").toString()));

        }



        packet.setRtd(Integer.parseInt(appSpecificReport.get("rtd").toString()));
        packet.setPayloadType(appSpecificReport.get("payloadtype").toString());



        packet.setType1(sourceDescription.get("type").toString());
        packet.setName1(sourceDescription.get("name").toString());


        if(entry.get("pcktLossPct") != null)  packet.setPcktLossPct(entry.get("pcktLossPct").toString());

        if( entry.get("rtpDSCP") == null ){ packet.setRtpDSCP("0"); } else { packet.setRtpDSCP("0"); }

        packet.setInsertTime(LocalDateTime.now());

        return packet;

    }


    @Override
    public void close() {

    }
}
