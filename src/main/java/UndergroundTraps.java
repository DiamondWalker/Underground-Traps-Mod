import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
	modid = UndergroundTraps.MODID,
	name = UndergroundTraps.NAME,
	version = UndergroundTraps.VERSION
)
public class UndergroundTraps {
	public static final String MODID = "undergroundtraps";
	public static final String NAME = "Underground Traps";
	public static final String VERSION = "1.0";
	
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		GameRegistry.registerWorldGenerator(new TrapGenerator(), 15);
	}
}
