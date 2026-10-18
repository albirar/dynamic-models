package cat.albirar.dynmod.demo.models.filters;

import lombok.Getter;

@Getter
public enum FiltersModel {
	Brand("B"),
	Market("M"),
	Parts("P"),
	Economic("C")
	;
	private String code;
	private FiltersModel(String code) {
		this.code = code;
	}
}
