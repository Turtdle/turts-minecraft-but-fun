"""Skydio X2D-inspired folding reconnaissance quad; ~66 x 57 x 12 cm unfolded.
At 3.35 cm/unit: approximately 19.7 x 17 x 3.6 units; 1.3 kg reference class.
Rest = flight-ready, feet at Y=0; item clip folds the four hinged arms and turns the
two-blade props to lie along them (as the X2D packs), carried small and low on the right.
"""
import drones as D
from armorygen import Model


def model() -> Model:
    m = D.new_model("drone_scout")
    D.scout_airframe(m)
    return D.finish_drone(m, folded=True, fold=(5.3, 3.15),
                          carry_scale=0.55, carry=(8.0, -4.0, -5.0), carry_yaw=5)
