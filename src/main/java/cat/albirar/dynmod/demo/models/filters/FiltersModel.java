package cat.albirar.dynmod.demo.models.filters;

import lombok.Getter;

@Getter
public enum FiltersModel {
	Brand("B"),
	Market("M"),
	Parts("P"),
	Economic("E")
	;
	private String code;
	private FiltersModel(String code) {
		this.code = code;
	}

	/**
	 * Returns the FiltersModel corresponding to the given code.
	 * 
	 * @param code the code to look up
	 * @return the corresponding FiltersModel
	 * @throws IllegalArgumentException if the code is not valid
	 */
	public static FiltersModel fromCode(String code) {
		for (FiltersModel filter : values()) {
			if (filter.code.equalsIgnoreCase(code)) {
				return filter;
			}
		}
		throw new IllegalArgumentException("Invalid filter code: " + code);
	}
}
