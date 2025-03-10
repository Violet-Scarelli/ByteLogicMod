package bytelogic.world.blocks.logic;

import arc.math.geom.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import bytelogic.*;
import bytelogic.gen.*;
import bytelogic.type.*;
import bytelogic.ui.guide.*;
import bytelogic.world.blocks.logic.SignalBlock.*;
import bytelogic.world.meta.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.ui.*;
import mindustry.world.*;
import mma.io.*;
import org.jetbrains.annotations.*;

public class SignalTransformer extends UnaryLogicBlock{
    protected static final Signal tmpSignal = new Signal();
    protected static final ByteWrites tmpWrites = new ByteWrites();
    protected static final ByteReads tmpRead = new ByteReads();

    public SignalTransformer(String name){
        super(name);
        /*this.<byte[], SignalTransformerBuild>config(byte[].class, (build, bytes) -> {
            build.selectedTypeSignal.fromBytes(bytes);
        });*/
     /*   this.<Integer, SignalTransformerBuild>config(Integer.class, (build, id) -> {
            SignalType type = SignalType.all[id];
            if(type == SignalTypes.nilType)
                type = SignalTypes.numberType;
            build.selectedType = type;
        });*/
        lastConfig = null;
        this.<byte[], SignalTransformerBuild>config(byte[].class, (build, bytes) -> {
            if(bytes.length < 2) return;
            Container.set(bytes);
            build.selectedType = Container.selectedType;
            build.inputType = Container.inputType;
        });
        this.<String, SignalTransformerBuild>config(String.class, (build, typeName) -> {
            SignalType type = SignalType.findByName(typeName);
            if(type == SignalTypes.nilType)
                type = SignalTypes.numberType;
            build.selectedType = type;
        });
        processor = it -> it;
    }

    protected static byte[] stateAsBytes(SignalType selectedType, byte inputType){
        tmpWrites.reset();
        tmpWrites.str("V_1");
        tmpWrites.str(selectedType.getName());
        tmpWrites.b(inputType);
        return tmpWrites.getBytes();
    }

    @Override
    public void drawPlanRegion(BuildPlan req, Eachable<BuildPlan> list){
        if(req.config instanceof byte[] bytes && bytes.length > 1){
            Container.set(bytes);
            byte[] config = {Container.inputType};
            req.config = config;

            super.drawPlanRegion(req, list);
            Container.inputType = config[0];
            req.config = Container.bytes();
        }else{
            super.drawPlanRegion(req, list);
        }
    }
    @Override
    public void flipRotation(BuildPlan req, boolean x){
        if(req.config instanceof byte[] bytes && bytes.length > 1){
            Container.set(bytes);
            byte[] config = {Container.inputType};
            req.config = config;

            super.flipRotation(req, x);
            Container.inputType = config[0];
            req.config = Container.bytes();
        }else{
            super.flipRotation(req, x);
        }
    }

    @Override
    public void init(){
        if(blockPreview == null){
            blockPreview = new BlockPreview(this, 5, 5, (world, isSwitch) -> {

                world.tile(0, 1).setBlock(byteLogicBlocks.signalBlock, Team.sharded, 0);

                world.tile(1, 1).setBlock(this, Team.sharded, 0);
                world.tile(2, 1).setBlock(byteLogicBlocks.relay, Team.sharded, 0);
//        world.tile(2, 1).build.<BinaryLogicBuild>as().inputType = 1;

                world.tile(2, 2).setBlock(byteLogicBlocks.signalBlock, Team.sharded, 0);
                world.tile(2, 2).build.<SignalLogicBuild>as().nextSignal.setNumber(-1);
                world.tile(3, 1).setBlock(byteLogicBlocks.displayBlock, Team.sharded);
                return new Point2[]{Tmp.p1.set(1, 1)};
            }){
                @Override
                public boolean shouldBuildConfiguration(@NotNull Block block){
                    return super.shouldBuildConfiguration(block) || block instanceof SignalTransformer;
                }
            };
            blockPreview.hasNoSwitchMirror(false);
        }
        super.init();
    }

    @Override
    public void setStats(){

        stats.add(BLStat.guide, table -> {
            table.button("Open guide", BLVars.modUI.guideDialog::show).with(t -> t.getLabel().setWrap(false));
        });
        super.setStats();
    }

    static class Container{
        static SignalType selectedType;
        static byte inputType;

        static void set(byte[] bytes){
            tmpRead.setBytes(bytes);
            String name = tmpRead.str();
            int version = 0;
            if(name.matches("(V_)[\\d]+")){
                version = Integer.parseInt(name.substring("V_".length()));
                name = tmpRead.str();
            }
            selectedType = SignalType.findByName(name);
            if(selectedType == SignalTypes.nilType){
                selectedType = SignalTypes.numberType;
            }
            if(version == 0){
                inputType = updateInputType(tmpRead.i());
            }else{
                inputType = tmpRead.b();
            }
        }

        public static byte[] bytes(){
            return stateAsBytes(selectedType, inputType);
        }
    }

    public class SignalTransformerBuild extends UnaryLogicBuild{
        SignalType selectedType = SignalTypes.numberType;

        @Override
        public void buildConfiguration(Table table){
            table.table(t -> {
                int i = 0;
                ButtonGroup<Button> group = new ButtonGroup<>();
                for(SignalType type : SignalType.all){
                    if(type == SignalTypes.nilType) continue;
                    t.button(type.getIcon(), Styles.squareTogglei, () -> {
                        configure(stateAsBytes(type, inputType));
                    }).size(48f).checked(selectedType == type).group(group);
                    i++;
                    if(i % 4 == 0){
                        t.row();
                    }
                }
            });
            table.row();
            super.buildConfiguration(table);
        }

        @Override
        protected void configureInputType(byte inputType){
            configure(stateAsBytes(selectedType, inputType));
        }

        @Override
        public void beforeUpdateSignalState(){
            if(doOutput && canOutputSignal((byte)rotation)){
                lastSignal.type = selectedType;
                front().<ByteLogicBuildingc>as().acceptSignal(this, lastSignal);
            }
        }

        @Override
        public void updateSignalState(){
            lastSignal.set(nextSignal);
            lastSignal.type = selectedType;
            nextSignal.setZero();

        }

        @Override
        public Object config(){
            return stateAsBytes(selectedType, inputType);
        }

        @Override
        public void customWrite(Writes write){
            write.str(selectedType.getName());
        }

        @Override
        public void customRead(Reads read){
            selectedType = SignalType.findByName(read.str());
            if(selectedType == SignalTypes.nilType){
                selectedType = SignalTypes.numberType;
            }
        }

        @Override
        public short customVersion(){
            return 1;
        }
    }
}
