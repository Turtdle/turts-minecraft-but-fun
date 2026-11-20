"""Seven-inch FPV quad reference: 30 cm motor diagonal, ~45 cm prop envelope.
Motor diagonal 8.97 units, props 5.3 units (17.8 cm); fixed camera pitches up 25 deg.
Carbon X frame, top LiPo and cosmetic olive payload; feet stand at Y=0.
The item clip carries it small and low on the right (see drones.finish_drone).
"""
import drones as D
from armorygen import Model


def model() -> Model:
    m = D.new_model("drone_fpv")
    D.fpv_airframe(m)
    return D.finish_drone(m, carry_scale=0.65, carry=(8.0, -4.0, -4.0), carry_yaw=15)
