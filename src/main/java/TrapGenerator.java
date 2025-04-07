import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockStone;
import net.minecraft.block.BlockTripWire;
import net.minecraft.block.BlockTripWireHook;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class TrapGenerator implements IWorldGenerator {
    public static final ResourceLocation ARROW_TRAP_DISPENSER = new ResourceLocation("undergroundtraps:underground_trap_dispenser");
    private static final BlockPos[] TNT_OFFSETS;

    static {
        TNT_OFFSETS = new BlockPos[3 * 3 * 3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    TNT_OFFSETS[9 * x + 3 * y + z] = new BlockPos(-1 + x, -y, -1 + z);
                }
            }
        }
    }
    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (world.provider.getDimension() == DimensionType.OVERWORLD.getId()) {
            BlockPos pos = new BlockPos(chunkX * 16 + 8, world.getSeaLevel() - 10, chunkZ * 16 + 8);
            ArrayList<BlockPos> possibleSpawns = new ArrayList<>();
            while (pos.getY() >= 0) {
                if (world.getBlockState(pos).getBlock() == Blocks.AIR && world.getBlockState(pos.down()).getBlock() == Blocks.STONE) {
                    possibleSpawns.add(pos);
                }
                pos = pos.down();
            }
            if (!possibleSpawns.isEmpty()) {
                BlockPos spawnPos = possibleSpawns.get(random.nextInt(possibleSpawns.size()));
                if (random.nextBoolean()) {
                    attemptGenerateTNTTrap(world, spawnPos);
                } else {
                    attemptGenerateDispenserTrap(world, spawnPos, random);
                }
            }
        }
    }

    private static void attemptGenerateTNTTrap(World world, BlockPos pos) {
        BlockPos tntPos = pos.down(2);

        /*
        check if this is a valid spawn position for the trap
        we want the pressure plate to be camouflaged, so we check if it'd be placed on top of stone
        we also want all the TNT to be buried and not exposed
         */
        IBlockState state = world.getBlockState(pos.down());
        if (state.getBlock() != Blocks.STONE || state.getValue(BlockStone.VARIANT) != BlockStone.EnumType.STONE) return;
        for (BlockPos offset : TNT_OFFSETS) { // all the possible TNT locations
            for (EnumFacing dir : EnumFacing.VALUES) { // all the faces of this TNT location. If these are all opaque, the TNT is hidden
                if (!world.getBlockState(tntPos.add(offset).offset(dir)).isOpaqueCube()) {
                    return;
                }
            }
        }

        // actually generate the trap
        world.setBlockState(pos, Blocks.STONE_PRESSURE_PLATE.getDefaultState());
        //world.setBlockState(tntPos, Blocks.TNT.getDefaultState());
        for (BlockPos offset : TNT_OFFSETS) world.setBlockState(tntPos.add(offset), Blocks.TNT.getDefaultState());
    }

    private static void attemptGenerateDispenserTrap(World world, BlockPos pos, Random rand) {
        /*
        there are 2 ways we can generate this trap: north-to-south or east-to-west
        we need to pick one of the 2
        we start by determining how far we are from the possible endpoints in each direction, putting these endpoints in a hashmap
         */
        HashMap<EnumFacing, Integer> distancesToWalls = new HashMap<>();
        boolean dispenserSide = rand.nextBoolean(); // this boolean will be used in determining which of the two hooks the dispenser will be over
        for (EnumFacing dir : EnumFacing.HORIZONTALS) {
            int dist = 0;
            BlockPos currPos = new BlockPos(pos);
            while (dist < 7 && world.getBlockState(currPos.down()).isOpaqueCube()) { // if we've traveled too far, or if the wire is in the air, abandon this direction
                /*
                tripwire hooks are very visible. We want to hide them inside the cave walls.
                If every block surrounding our current position (excluding the direction we just came from) is solid, this is a good place to end the line & hide the hook
                 */
                boolean isEnd = true;
                for (EnumFacing dir2 : EnumFacing.VALUES) {
                    if (dir != dir2.getOpposite() && !world.getBlockState(currPos.offset(dir2)).isOpaqueCube()) {
                        isEnd = false;
                        break;
                    }
                }

                /*
                additionally, we also want the dispenser to be buried in the wall.
                This means that the hook under the dispenser will be pushed further into the wall
                 */
                if (dir == (dispenserSide ? EnumFacing.NORTH : EnumFacing.SOUTH) || dir == (dispenserSide ? EnumFacing.EAST : EnumFacing.WEST)) { // determine dispenser will generate over this hook
                    BlockPos abovePos = currPos.up(); // the dispenser's position
                    for (EnumFacing dir3 : EnumFacing.VALUES) {
                        if (dir3 != EnumFacing.DOWN && !world.getBlockState(abovePos.offset(dir3)).isOpaqueCube()) { // the block under the dispenser will be the hook so that's ignored
                            isEnd = false;
                            break;
                        }
                    }
                }

                if (isEnd) {
                    distancesToWalls.put(dir, dist);
                    break;
                } else {
                    currPos = currPos.offset(dir);
                    dist++;
                }
            }
        }
        if (distancesToWalls.isEmpty()) return; // if for whatever reason no directions are valid, just give up right here

        /*
        now that we have all the data, we can decide which of the 2 lines we use.
        we'll pick the shortest line, so we need to know the total length of each of the 2 lines
        a value of Integer.MAX_VALUE means we've determined this line isn't possible at all
         */
        int northSouthDistance = Integer.MAX_VALUE, eastWestDistance = Integer.MAX_VALUE;
        if (distancesToWalls.containsKey(EnumFacing.NORTH) && distancesToWalls.containsKey(EnumFacing.SOUTH)) {
            if (distancesToWalls.get(EnumFacing.NORTH) + distancesToWalls.get(EnumFacing.SOUTH) > 1) {
                northSouthDistance = distancesToWalls.get(EnumFacing.NORTH) + distancesToWalls.get(EnumFacing.SOUTH);
            }
        }
        if (distancesToWalls.containsKey(EnumFacing.EAST) && distancesToWalls.containsKey(EnumFacing.WEST)) {
            if (distancesToWalls.get(EnumFacing.EAST) + distancesToWalls.get(EnumFacing.WEST) > 1) {
                eastWestDistance = distancesToWalls.get(EnumFacing.EAST) + distancesToWalls.get(EnumFacing.WEST);
            }
        }

        /*
        now we pick the line using all the info we've gathered. Again we pick the shortest one
        if both are equal we pick a random one
         */
        EnumFacing chosenDirection;
        if (northSouthDistance != Integer.MAX_VALUE && (northSouthDistance < eastWestDistance || (northSouthDistance == eastWestDistance && rand.nextBoolean()))) {
            chosenDirection = EnumFacing.NORTH;
        } else if (eastWestDistance != Integer.MAX_VALUE) {
            chosenDirection = EnumFacing.EAST;
        } else {
            return; // neither line is valid so we give up
        }

        /*
        now it's time to actually generate the line
         */
        EnumFacing oppositeDirection = chosenDirection.getOpposite();
        int distance = distancesToWalls.get(chosenDirection), oppositeDistance = distancesToWalls.get(oppositeDirection);

        // place the hooks
        world.setBlockState(pos.offset(chosenDirection, distance), Blocks.TRIPWIRE_HOOK.getDefaultState()
                .withProperty(BlockTripWireHook.FACING, oppositeDirection)
                .withProperty(BlockTripWireHook.ATTACHED, Boolean.TRUE
        ));
        world.setBlockState(pos.offset(oppositeDirection, oppositeDistance), Blocks.TRIPWIRE_HOOK.getDefaultState()
                .withProperty(BlockTripWireHook.FACING, chosenDirection)
                .withProperty(BlockTripWireHook.ATTACHED, Boolean.TRUE
        ));

        // place the dispenser
        BlockPos dispenserPos = pos.offset(dispenserSide ? chosenDirection : oppositeDirection, dispenserSide ? distance : oppositeDistance).up();
        EnumFacing dispenerDir = dispenserSide ? oppositeDirection : chosenDirection;
        world.setBlockState(dispenserPos, Blocks.DISPENSER.getDefaultState()
                .withProperty(BlockDispenser.FACING, dispenerDir));

        TileEntity tileentity = world.getTileEntity(dispenserPos);

        if (tileentity instanceof TileEntityDispenser)
        {
            ((TileEntityDispenser)tileentity).setLootTable(ARROW_TRAP_DISPENSER, rand.nextLong());
        }

        for (BlockPos b = dispenserPos.offset(dispenerDir); b.getX() != pos.getX() || b.getZ() != pos.getZ(); b = b.offset(dispenerDir)) {
            world.setBlockState(b, Blocks.AIR.getDefaultState());
        }

        // place the wire
        for (int i = oppositeDistance > 0 ? 0 : 1; i < distance; i++) { // sometimes the opposite hook will spawn at offset 0. If this happened, we must skip this index
            world.setBlockState(pos.offset(chosenDirection, i), Blocks.TRIPWIRE.getDefaultState()
                    .withProperty(BlockTripWire.ATTACHED, Boolean.TRUE)
            );
        }
        for (int i = 1; i < oppositeDistance; i++) { // here we can skip 1 since it was already placed the first time
            world.setBlockState(pos.offset(oppositeDirection, i), Blocks.TRIPWIRE.getDefaultState()
                    .withProperty(BlockTripWire.ATTACHED, Boolean.TRUE)
            );
        }
    }
}
